/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
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

// java
import java.io.File
import java.util.UUID

// cats
import cats.instances.list._
import cats.instances.either._
import cats.syntax.either._
import cats.syntax.traverse._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.compact

// iglu scala client
import com.snowplowanalytics.iglu.client.Resolver
import com.snowplowanalytics.iglu.client.validation.ValidatableJValue.validate
import com.snowplowanalytics.iglu.client.repositories.{ RepositoryRefConfig, EmbeddedRepositoryRef }

// this project
import FileUtils.getJsonFromFile
import IgluctlConfig.IgluctlAction
import Utils.extractKey

case class DeployCommand(config: File) extends Command.CtlCommand {
  import DeployCommand._

  /**
    * Primary method of static deploy command
    * Performs usual schema workflow at once, per configuration file
    * Short-circuits on first failed step
    */
  def process(): Unit = {
    val igluctlConfig = for {
      configDoc <- getJsonFromFile(config)
      config <- validate(configDoc, true)(resolver).toEither.leftMap(_.list.mkString(", "))
      igluctlConfig <- extractConfig(config).leftMap(_.toList.mkString(", "))
    } yield igluctlConfig

    igluctlConfig match {
      case Right(ctlConfig) =>
        ctlConfig.lintCommand.process()
        ctlConfig.generateCommand.processDdl()
        ctlConfig.actions.foreach { action => action.process() }
      case Left(err) =>
        System.err.println("Invalid configuration")
        System.err.println(err)
        sys.exit(1)
    }
  }
}

object DeployCommand {

  val resolver: Resolver = {
    val embeddedRepo = RepositoryRefConfig("Igluctl Embedded", 1, List("com.snowplowanalytics.iglu"))
    val embeddedIglu = EmbeddedRepositoryRef(embeddedRepo, "/igluctl")
    Resolver(5, List(embeddedIglu))
  }

  /** Configuration key that can represent either "hard-coded" value or env var with API key */
  private[ctl] sealed trait ApiKeySecret {
    def value: Either[String, UUID]
  }

  private[ctl] object ApiKeySecret {
    case class Plain(uuid: UUID) extends ApiKeySecret {
      def value: Either[String, UUID] = uuid.asRight
    }
    case class EnvVar(name: String) extends ApiKeySecret {
      def value: Either[String, UUID] =
        for {
          value <- Option(System.getenv().get(name)).toRight(s"Environment variable $name is not available")
          uuid <- Either.catchNonFatal(UUID.fromString(value)).leftMap(_.getMessage)
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
  def extractConfig(config: JValue): Either[String, IgluctlConfig] = {
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
  def extractLint(input: String, doc: JValue): Either[String, LintCommand] = {
    for {
      skipWarnings <- extractKey[Boolean](doc, "skipWarnings")
      includedChecks <- extractKey[List[String]](doc, "includedChecks")
      linters <- LintCommand.includeLinters(includedChecks.mkString(","))
    } yield LintCommand(new File(input), skipWarnings, linters)
  }

  /** Extract `static generate` options from igluctl config */
  def extractGenerate(input: String, doc: JValue): Either[String, GenerateCommand] = {
    for {
      output <- extractKey[String](doc, "output")
      withJsonPaths <-  extractKey[Boolean](doc, "withJsonPaths")
      dbSchema <- extractKey[Option[String]](doc, "dbSchema")
      varcharSize <- extractKey[Int](doc, "varcharSize")
      noHeader <- extractKey[Option[Boolean]](doc, "noHeader")
      force <- extractKey[Boolean](doc, "force")
      owner <- extractKey[Option[String]](doc, "owner")
    } yield GenerateCommand(new File(input), new File(output), "redshift", withJsonPaths, false, dbSchema, varcharSize,
      false, noHeader.getOrElse(false), force, owner)
  }

  /** Extract `s3cp` or `push` commands from igluctl config */
  def extractAction(input: String, actionDoc: JValue): Either[String, IgluctlAction] = {
    actionDoc \ "action" match {
      case JString("s3cp") =>
        for {
          bucket <- extractKey[String](actionDoc, "bucketPath")
          profile <- extractKey[Option[String]](actionDoc, "profile")
          region <- extractKey[Option[String]](actionDoc, "region")
        } yield S3cpCommand(new File(input), bucket, None, None, None, profile, region)

      case JString("push") =>
        for {
          registryRoot <- extractKey[String](actionDoc, "registry")
          secret <- extractKey[ApiKeySecret](actionDoc, "apikey")
          masterApiKey <- secret.value
          isPublic <- extractKey[Boolean](actionDoc, "isPublic")
        } yield PushCommand(PushCommand.parseRegistryRoot(registryRoot).toOption.get, masterApiKey, new File(input), isPublic)
      case JString(action) => s"Unrecognized action $action".asLeft
      case _ => "Action can only be a string".asLeft
    }
  }

  /** Extract list of actions from igluctl config */
  def extractActions(input: String, actions: JValue): Either[String, List[IgluctlAction]] = {
    actions match {
      case JNull => List.empty[IgluctlAction].asRight
      case JArray(actions: List[JValue]) => actions.traverse(extractAction(input, _))
      case _ => "Actions can be either array or null".asLeft
    }
  }
}
