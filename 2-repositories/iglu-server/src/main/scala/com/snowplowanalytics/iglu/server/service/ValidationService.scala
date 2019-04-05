/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.iglu.server.service

import io.circe.Json

import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.rho.{RhoMiddleware, RhoRoutes, AuthedContext}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.swagger.syntax.io

import cats.data.{NonEmptyList, ValidatedNel, Validated}
import cats.effect.{IO, Sync}
import cats.implicits._

import com.snowplowanalytics.iglu.core.{SelfDescribingData, SelfDescribingSchema, SchemaMap}
import com.snowplowanalytics.iglu.core.circe.instances._

import com.snowplowanalytics.iglu.client.validator.ValidatorError
import com.snowplowanalytics.iglu.client.validator.CirceValidator
import com.snowplowanalytics.iglu.schemaddl.jsonschema.circe.implicits._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{Pointer, SelfSyntaxChecker, Schema => SchemaAst}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SanityLinter.lint
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.{ allLintersMap, Message, Level }

import com.snowplowanalytics.iglu.server.storage.Storage
import com.snowplowanalytics.iglu.server.middleware.PermissionMiddleware
import com.snowplowanalytics.iglu.server.model.{ IgluResponse, Permission, Schema }
import com.snowplowanalytics.iglu.server.codecs.JsonCodecs._
import com.snowplowanalytics.iglu.server.codecs.UriParsers._
import com.snowplowanalytics.iglu.server.Utils._

class ValidationService[F[+_]: Sync](swagger: SwaggerSyntax[F],
                                     ctx: AuthedContext[F, Permission],
                                     db: Storage[F]) extends RhoRoutes[F] {
  import swagger._
  import ValidationService._

  "This route allows you to validate schemas" **
    POST / "validate" / "schema" / pathVar[Schema.Format] ^ jsonDecoder[F] |>> validateSchema _

  "This route allows you to validate self-describing instances" **
    POST / "validate" / "instance" >>> ctx.auth ^ jsonDecoder[F] |>> validateData _

  def validateSchema(format: Schema.Format, schema: Json) =
    format match {
      case Schema.Format.Jsonschema =>
        validateJsonSchema(schema) match {
          case Validated.Valid(sd) =>
            val message = s"The schema provided is a valid self-describing ${sd.self.schemaKey.toSchemaUri} schema"
            Ok(IgluResponse.Message(message): IgluResponse)
          case Validated.Invalid(report) =>
            Ok(IgluResponse.SchemaValidationReport(report): IgluResponse)
        }
    }

  def validateData(authInfo: Permission, instance: Json) = {
    SelfDescribingData.parse(instance) match {
      case Right(SelfDescribingData(key, data)) =>
        for {
          schema <- db.getSchema(SchemaMap(key))
          response <- schema match {
            case Some(Schema(_, meta, schemaBody)) if meta.isPublic || authInfo.canRead(key.vendor) =>
              CirceValidator.validate(data, schemaBody) match {
                case Left(ValidatorError.InvalidData(report)) =>
                  Ok(IgluResponse.InstanceValidationReport(report): IgluResponse)
                case Left(ValidatorError.InvalidSchema(_)) =>
                  val message = s"Schema ${key.toSchemaUri} fetched from DB is invalid"
                  InternalServerError(IgluResponse.Message(message): IgluResponse)
                case Right(_) =>
                  Ok(IgluResponse.Message(s"Instance is valid ${key.toSchemaUri}"): IgluResponse)
              }
            case _ =>
              NotFound(IgluResponse.SchemaNotFound: IgluResponse)
          }
        } yield response
      case Left(error) =>
        BadRequest(IgluResponse.Message(s"JSON payload is not self-describing, ${error.code}"): IgluResponse)
    }

  }
}

object ValidationService {

  def asRoutes(db: Storage[IO],
               ctx: AuthedContext[IO, Permission],
               rhoMiddleware: RhoMiddleware[IO]): HttpRoutes[IO] = {
    val service = new ValidationService[IO](io, ctx, db).toRoutes(rhoMiddleware)
    PermissionMiddleware.wrapService(db, ctx, service)
  }

  type LintReport[A] = ValidatedNel[Message, A]

  val NotSelfDescribing = Message(Pointer.Root, "JSON Schema is not self-describing", Level.Error)
  val NotSchema = Message(Pointer.Root, "Cannot extract JSON Schema", Level.Error)

  def validateJsonSchema(schema: Json): LintReport[SelfDescribingSchema[Json]] = {
    val generalCheck =
      SelfSyntaxChecker.validateSchema(schema.fromCirce, false)

    val selfDescribingCheck = SelfDescribingSchema
      .parse(schema)
      .fold(_ => NotSelfDescribing.invalidNel[SelfDescribingSchema[Json]], _.validNel[Message])

    val lintReport: LintReport[Unit] = SchemaAst.parse(schema)
      .fold(NotSchema.invalidNel[SchemaAst])(_.validNel[Message])
      .andThen { ast =>
        val result = lint(ast, allLintersMap.values.toList)
          .toList
          .flatMap { case (pointer, issues) => issues.toList.map(_.toMessage(pointer)) }
        NonEmptyList.fromList(result).fold(().validNel[Message])(_.invalid[Unit])
      }

    (generalCheck, lintReport, selfDescribingCheck).mapN { (_, _, schema) => schema }
  }
}
