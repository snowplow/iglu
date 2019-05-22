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
package com.snowplowanalytics.iglu.server
package service

import cats.effect._
import cats.implicits._

import io.circe.Json

import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.rho.bits.TextMetaData
import org.http4s.rho.{AuthedContext, RhoMiddleware, RhoRoutes}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.swagger.syntax.{io => swaggerSyntax}

import com.snowplowanalytics.iglu.core.{SchemaMap, SchemaVer, SelfDescribingSchema}
import com.snowplowanalytics.iglu.core.circe.CirceIgluCodecs._

import com.snowplowanalytics.iglu.server.codecs._
import com.snowplowanalytics.iglu.server.storage.Storage
import com.snowplowanalytics.iglu.server.middleware.PermissionMiddleware
import com.snowplowanalytics.iglu.server.model.{IgluResponse, Permission, Schema, VersionCursor}
import com.snowplowanalytics.iglu.server.model.Schema.SchemaBody
import com.snowplowanalytics.iglu.server.model.Schema.Repr.{Format => SchemaFormat}
import com.snowplowanalytics.iglu.server.model.VersionCursor.Inconsistency

class SchemaService[F[+_]: Sync](swagger: SwaggerSyntax[F],
                                 ctx: AuthedContext[F, Permission],
                                 db: Storage[F],
                                 patchesAllowed: Boolean,
                                 webhooks: Webhook.WebhookClient[F]) extends RhoRoutes[F] {

  import swagger._
  import SchemaService._
  implicit val C: Clock[F] = Clock.create[F]

  val reprUri = genericQueryCapture(UriParsers.parseRepresentationUri[F]).withMetadata(ReprMetadata)
  val reprCanonical = genericQueryCapture(UriParsers.parseRepresentationCanonical[F]).withMetadata(ReprMetadata)

  val version = pathVar[SchemaVer.Full]("version", "SchemaVer")
  val isPublic = paramD[Boolean]("isPublic", false, "Should schema be created as public")

  val schemaOrJson = jsonOf[F, SchemaBody]
  val schema = jsonOf[F, SelfDescribingSchema[Json]]

  private val validationService = new ValidationService[F](swagger, ctx, db)

  "Get a particular schema by its Iglu URI" **
    GET / 'vendor / 'name / 'format / version +? reprCanonical >>> ctx.auth |>> getSchema _

  "Delete a particular schema from Iglu by its URI" **
    DELETE / 'vendor / 'name / 'format / version >>> ctx.auth |>> deleteSchema _

  "Get list of schemas by vendor name" **
    GET / 'vendor / 'name +? reprUri >>> ctx.auth |>> getSchemasByName _

  "Get list of schemas by vendor name" **
    GET / 'vendor / 'name / "jsonschema" / pathVar[Int]("model") +? reprUri >>> ctx.auth |>> getSchemasByModel _

  "Get all schemas for vendor" **
    GET / 'vendor +? reprUri >>> ctx.auth |>> getSchemasByVendor _

  "List all available schemas" **
    GET +? reprUri >>> ctx.auth |>> listSchemas _

  "Add a schema (self-describing or not) to its Iglu URI" **
    PUT / 'vendor / 'name / 'format / version +? isPublic >>> ctx.auth ^ schemaOrJson |>> putSchema _

  "Publish new self-describing schema" **
    POST +? isPublic >>> ctx.auth ^ schema |>> publishSchema _


  "Schema validation endpoint (deprecated)" **
    POST / "validate" / 'vendor / 'name / "jsonschema" / 'version ^ jsonDecoder[F] |>> {
    (_: String, _: String, _: String, json: Json) =>
      validationService.validateSchema(Schema.Format.Jsonschema, json)
  }


  def getSchema(vendor: String, name: String, format: String, version: SchemaVer.Full,
                schemaFormat: SchemaFormat, permission: Permission) = {
    db.getSchema(SchemaMap(vendor, name, format, version)).flatMap {
      case Some(schema) if schema.metadata.isPublic => Ok(schema.withFormat(schemaFormat))
      case Some(schema) if permission.canRead(schema.schemaMap.schemaKey.vendor) => Ok(schema.withFormat(schemaFormat))
      case _ => NotFound(IgluResponse.SchemaNotFound: IgluResponse)
    }
  }

  def deleteSchema(vendor: String, name: String, format: String, version: SchemaVer.Full, permission: Permission) = {
    permission match {
      case Permission.Master if patchesAllowed =>
        db.deleteSchema(SchemaMap(vendor, name, format, version)) *> Ok(IgluResponse.Message("Schema deleted"): IgluResponse)
      case Permission.Master =>
        MethodNotAllowed(IgluResponse.Message("DELETE is forbidden on production registry"): IgluResponse)
      case _ => Unauthorized(IgluResponse.Message("Not enough permissions"): IgluResponse)
    }
  }

  def getSchemasByName(vendor: String, name: String, format: SchemaFormat, permission: Permission) = {
    val response = db
      .getSchemasByVendorName(vendor, name)
      .filter(isReadable(permission))
      .map(_.withFormat(format))
    Ok(JsonArrayStream(response))
  }

  def getSchemasByVendor(vendor: String, format: SchemaFormat, permission: Permission) = {
    val response = db
      .getSchemasByVendor(vendor, false)
      .filter(isReadable(permission))
      .map(_.withFormat(format))
    Ok(JsonArrayStream(response))
  }

  def getSchemasByModel(vendor: String, name: String, model: Int, format: SchemaFormat, permission: Permission) = {
    val response = db
      .getSchemasByVendorName(vendor, name)
      .filter(isReadable(permission))
      .filter(_.schemaMap.schemaKey.version.model == model)
      .map(_.withFormat(format))
    Ok(JsonArrayStream(response))
  }

  def publishSchema(isPublic: Boolean, permission: Permission, schema: SelfDescribingSchema[Json]) =
    addSchema(permission, schema, isPublic)

  def putSchema(vendor: String, name: String, format: String, version: SchemaVer.Full, isPublic: Boolean,
                permission: Permission,
                json: SchemaBody) = json match {
    case SchemaBody.BodyOnly(body) =>
      val schemaMap = SchemaMap(vendor, name, format, version)
      addSchema(permission, SelfDescribingSchema(schemaMap, body), isPublic)
    case SchemaBody.SelfDescribing(schema) =>
      val schemaMapUri = SchemaMap(vendor, name, format, version)
      if (schemaMapUri == schema.self) addSchema(permission, schema, isPublic)
      else BadRequest(IgluResponse.SchemaMismatch(schemaMapUri.schemaKey, schema.self.schemaKey): IgluResponse)
  }

  def listSchemas(format: SchemaFormat, permission: Permission) = {
    val response = db.getSchemas.filter(isReadable(permission)).map(_.withFormat(format))
    Ok(JsonArrayStream(response))
  }

  private def addSchema(permission: Permission, schema: SelfDescribingSchema[Json], isPublic: Boolean) =
    if (permission.canCreateSchema(schema.self.schemaKey.vendor))
      for {
        allowed <- isSchemaAllowed(db, schema.self, patchesAllowed, isPublic)
        response <- allowed match {
          case Right(_) =>
            for {
              existing <- db.getSchema(schema.self).map(_.isDefined)
              _        <- if (existing) db.updateSchema(schema.self, schema.schema, isPublic) else db.addSchema(schema.self, schema.schema, isPublic)
              payload   = IgluResponse.SchemaUploaded(existing, schema.self.schemaKey): IgluResponse
              _        <- webhooks.schemaPublished(schema.self.schemaKey, existing)
              response <- if (existing) Ok(payload) else Created(payload)
            } yield response
          case Left(error) =>
            Conflict(IgluResponse.Message(error): IgluResponse)
        }
      } yield response
    else Forbidden(IgluResponse.Forbidden: IgluResponse)
}

object SchemaService {

  val ReprMetadata: TextMetaData = new TextMetaData {
    def msg: String =
      "Schema representation format (can be specified either by repr=uri/meta/canonical or legacy meta=1&body=1)"
  }

  def asRoutes(patchesAllowed: Boolean, webhook: Webhook.WebhookClient[IO])
              (db: Storage[IO],
               ctx: AuthedContext[IO, Permission],
               rhoMiddleware: RhoMiddleware[IO]): HttpRoutes[IO] = {
    val service = new SchemaService(swaggerSyntax, ctx, db, patchesAllowed, webhook).toRoutes(rhoMiddleware)
    PermissionMiddleware.wrapService(db, ctx, service)
  }

  def isSchemaAllowed[F[_]: Sync](db: Storage[F],
                                  schemaMap: SchemaMap,
                                  patchesAllowed: Boolean,
                                  isPublic: Boolean): F[Either[String, Unit]] =
    for {
      schemas        <- db.getSchemasByVendorName(schemaMap.schemaKey.vendor, schemaMap.schemaKey.name).compile.toList
      previousPublic  = schemas.forall(_.metadata.isPublic)
      versions        = schemas.map(_.schemaMap.schemaKey.version)
    } yield
      (if ((previousPublic && isPublic) || (!previousPublic && !isPublic) || schemas.isEmpty)
        VersionCursor.isAllowed(schemaMap.schemaKey.version, versions, patchesAllowed)
      else Inconsistency.Availability(isPublic, previousPublic).asLeft).leftMap(_.show)

  /** Extract schemas from database, available for particular permission */
  def isReadable(permission: Permission)(schema: Schema): Boolean =
    permission.canRead(schema.schemaMap.schemaKey.vendor) || schema.metadata.isPublic
}