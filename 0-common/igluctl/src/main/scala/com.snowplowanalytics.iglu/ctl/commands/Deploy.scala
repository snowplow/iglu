/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.iglu.ctl
package commands

import java.nio.file.{Path, Paths}
import java.util.UUID

import cats.data.{ EitherT, NonEmptyList }
import cats.effect.IO
import cats.implicits._

import com.snowplowanalytics.iglu.client.Resolver
import com.snowplowanalytics.iglu.client.repositories.{EmbeddedRepositoryRef, RepositoryRefConfig}
import com.snowplowanalytics.iglu.client.validation.ValidatableJValue.validate

import com.snowplowanalytics.iglu.ctl.File.readFile
import com.snowplowanalytics.iglu.ctl.IgluctlConfig.IgluctlAction
import com.snowplowanalytics.iglu.ctl.Utils.extractKey
import com.snowplowanalytics.iglu.ctl.Common.Error

import org.json4s._
import org.json4s.jackson.JsonMethods.compact


object Deploy {

  lazy val resolver: Resolver = {
    val embeddedRepo = RepositoryRefConfig("Igluctl Embedded", 1, List("com.snowplowanalytics.iglu"))
    val embeddedIglu = EmbeddedRepositoryRef(embeddedRepo, "/igluctl")
    Resolver(5, List(embeddedIglu))
  }

  /**
    * Primary method of static deploy command
    * Performs usual schema workflow at once, per configuration file
    * Short-circuits on first failed step
    */
  def process(configFile: Path): Result = {
    for {
      configDoc  <- EitherT(readFile(configFile).map(_.flatMap(_.asJson).toEitherNel))
      config     <- EitherT(IO(validate(configDoc.content, true)(resolver)
        .toEither
        .leftMap(messages => NonEmptyList.fromListUnsafe(messages.list.map(message => Error.ConfigParseError(message.toString))))))
      cfg        <- EitherT.fromEither[IO](extractConfig(config).toEitherNel)
      output     <- Lint.process(cfg.lint.input, cfg.lint.skipChecks, cfg.lint.skipWarnings)
      _          <- Generate.process(cfg.generate.input,
        cfg.generate.output, cfg.generate.withJsonPaths, cfg.generate.rawMode,
        cfg.generate.dbSchema, cfg.generate.varcharSize, cfg.generate.splitProduct,
        cfg.generate.noHeader, cfg.generate.force, cfg.generate.owner)
      actionsOut <- cfg.actions.traverse[EitherT[IO, NonEmptyList[Common.Error], ?], List[String]](_.process)
    } yield output ::: actionsOut.flatten
  }

  /** Configuration key that can represent either "hard-coded" value or env var with API key */
  private[ctl] sealed trait ApiKeySecret {
    def value: Either[Error, UUID]
  }

  private[ctl] object ApiKeySecret {
    case class Plain(uuid: UUID) extends ApiKeySecret {
      def value: Either[Error, UUID] = uuid.asRight
    }
    case class EnvVar(name: String) extends ApiKeySecret {
      def value: Either[Error, UUID] =
        for {
          value <- Option(System.getenv().get(name)).toRight(Error.ConfigParseError(s"Environment variable $name is not available"))
          uuid <- Either.catchNonFatal(UUID.fromString(value)).leftMap(e => Error.ConfigParseError(e.getMessage))
        } yield uuid
    }

    def deserialize(json: JValue): Either[MappingException, ApiKeySecret] = json match {
      case JString(value) if value.startsWith("$") => EnvVar(value.drop(1)).asRight
      case JString(uuid) => Either.catchNonFatal(UUID.fromString(uuid)) match {
        case Right(plain) => Plain(plain).asRight
        case Left(error) => new MappingException(s"apikey can have ENVVAR or UUID format; ${error.getMessage}").asLeft
      }
      case _ => new MappingException(s"apikey must be a string, ${compact(json)} given").asLeft
    }

    val serialize: PartialFunction[Any, JValue] = {
      case EnvVar(name) => JString(name)
      case Plain(value) => JString(value.toString)
    }

    val deserializedPartial: PartialFunction[JValue, ApiKeySecret] = {
      case json => deserialize(json).fold(throw _, identity)
    }

    implicit object Serializer extends CustomSerializer[ApiKeySecret](
      (_: Formats) => (deserializedPartial, serialize)
    )
  }

  implicit val formats: Formats = DefaultFormats + ApiKeySecret.Serializer

  /** Parse igluctl config object from JSON */
  def extractConfig(config: JValue): Either[Error, IgluctlConfig] = {
    val description = (config \ "description").extractOpt[String]
    val jLint: JValue = config \ "lint"
    val jGenerate: JValue = config \ "generate"
    val jActions: JValue = config \ "actions"

    val inputExt = extractKey[String](config, "input")

    inputExt match {
      case Right(input) =>
        for {
          lint <- extractLint(input, jLint)
          generate <- extractGenerate(input, jGenerate)
          actions <- extractActions(input, jActions)
        } yield IgluctlConfig(description, lint, generate, actions)
      case Left(err) =>
        err.asLeft
    }
  }

  /** Extract `lint` command options from igluctl config */
  def extractLint(input: String, doc: JValue): Either[Error, Command.Lint] = {
    for {
      skipWarnings <- extractKey[Boolean](doc, "skipWarnings")
      includedChecks <- extractKey[List[String]](doc, "includedChecks")
      linters <- Lint.parseOptionalLinters(includedChecks.mkString(","))
    } yield Command.Lint(Paths.get(input), skipWarnings, linters)
  }

  /** Extract `static generate` options from igluctl config */
  def extractGenerate(input: String, doc: JValue): Either[Error, Command.StaticGenerate] = {
    for {
      output <- extractKey[String](doc, "output")
      withJsonPaths <-  extractKey[Boolean](doc, "withJsonPaths")
      dbSchema <- extractKey[Option[String]](doc, "dbSchema")
      varcharSize <- extractKey[Int](doc, "varcharSize")
      noHeader <- extractKey[Option[Boolean]](doc, "noHeader")
      force <- extractKey[Boolean](doc, "force")
      owner <- extractKey[Option[String]](doc, "owner")
    } yield Command.StaticGenerate(Paths.get(input), Paths.get(output), dbSchema.getOrElse("atomic"), owner, varcharSize, withJsonPaths, false,
      false, noHeader.getOrElse(false), force)
  }

  /** Extract `s3cp` or `push` commands from igluctl config */
  def extractAction(input: String, actionDoc: JValue): Either[Error, IgluctlAction] = {
    actionDoc \ "action" match {
      case JString("s3cp") =>
        for {
          bucket <- extractKey[String](actionDoc, "bucketPath")
          bucketPath <- bucket.stripPrefix("s3://").split("/").toList match {
            case b :: Nil => (b, None).asRight
            case b :: p => (b, Some(p.mkString("/"))).asRight
            case _ => Error.ConfigParseError("bukcetPath has invalid format").asLeft
          }
          (b, p) = bucketPath
          profile <- extractKey[Option[String]](actionDoc, "profile")
          region <- extractKey[Option[String]](actionDoc, "region")
        } yield IgluctlAction.S3Cp(Command.StaticS3Cp(Paths.get(input), b, p, None, None, profile, region))
      case JString("push") =>
        for {
          registryRoot <- extractKey[String](actionDoc, "registry").flatMap(Push.HttpUrl.parse)
          secret <- extractKey[ApiKeySecret](actionDoc, "apikey")
          masterApiKey <- secret.value
          isPublic <- extractKey[Boolean](actionDoc, "isPublic")
        } yield IgluctlAction.Push(Command.StaticPush(Paths.get(input), registryRoot, masterApiKey, isPublic))
      case JString(action) => Error.ConfigParseError(s"Unrecognized action $action").asLeft
      case _ => Error.ConfigParseError("Action can only be a string").asLeft
    }
  }

  /** Extract list of actions from igluctl config */
  def extractActions(input: String, actions: JValue): Either[Error, List[IgluctlAction]] = {
    actions match {
      case JNull => List.empty[IgluctlAction].asRight
      case JArray(actions: List[JValue]) => actions.traverse(extractAction(input, _))
      case _ => Error.ConfigParseError("Actions can be either array or null").asLeft
    }
  }
}
