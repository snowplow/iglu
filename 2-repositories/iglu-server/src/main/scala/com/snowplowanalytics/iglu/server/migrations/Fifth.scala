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
package migrations

import java.time.Instant
import java.util.UUID

import fs2.Stream

import cats.{ Applicative, MonadError }
import cats.implicits._

import io.circe.Json
import io.circe.parser.parse

import doobie._
import doobie.implicits._
import doobie.ConnectionIO
import doobie.postgres.implicits._
import doobie.postgres.circe.json.implicits._

import eu.timepit.refined.types.numeric.NonNegInt

import com.snowplowanalytics.iglu.core.{ ParseError, SchemaKey, SchemaMap, SchemaVer, SelfDescribingSchema }
import com.snowplowanalytics.iglu.core.circe.implicits._
import com.snowplowanalytics.iglu.server.model.VersionCursor
import com.snowplowanalytics.iglu.server.model.VersionCursor.Inconsistency

import model.{ Permission, Schema, SchemaDraft }
import storage.Postgres
import storage.Storage.IncompatibleStorage


/** Steps required to migrate the DB from pre-0.6.0 structure to current one */
object Fifth {

  val OldSchemasTable = Fragment.const("schemas")
  val OldPermissionsTable = Fragment.const("apikeys")

  case class SchemaFifth(map: SchemaMap, schema: Json, isPublic: Boolean, createdAt: Instant, updatedAt: Instant)

  def perform: ConnectionIO[Unit] =
    for {
      _ <- checkContent(querySchemas)
      schemas <- checkConsistency(querySchemas)
      _ <- checkContent(queryDrafts)
      _ <- Bootstrap.allStatements.sequence[ConnectionIO, Int].map(_.combineAll)
      _ <- (migrateKeys ++ migrateSchemas(schemas) ++ migrateDrafts).compile.drain
    } yield ()

  /** Perform query and check if entities are valid against current model, throw an exception otherwise */
  def checkContent[A](query: Query0[Either[String, A]]): ConnectionIO[Unit] = {
    val errors = query.stream.flatMap {
      case Right(_) => Stream.empty
      case Left(error) => Stream.emit(error)
    }
    errors.compile.toList.flatMap {
      case Nil => Applicative[ConnectionIO].pure(())
      case err =>
        val exception = IncompatibleStorage(s"Inconsistent entities found: ${err.mkString(", ")}")
        MonadError[ConnectionIO, Throwable].raiseError(exception)
    }
  }

  def checkConsistency(query: Query0[Either[String, SchemaFifth]]): ConnectionIO[List[SchemaFifth]] =
    query.stream.compile
      .toList
      .map { schemas => schemas.parTraverse(_.toEitherNel) }
      .flatMap { result =>
        result
          .leftMap(errors => s"Inconsistent entries found: ${errors.toList.mkString(", ")}")
          .flatTap(checkAllowance) match {
          case Right(schemas) =>
            Applicative[ConnectionIO].pure(schemas)
          case Left(errors) =>
            val exception = IncompatibleStorage(s"Inconsistent entities found: ${errors.toList.mkString(", ")}")
            MonadError[ConnectionIO, Throwable].raiseError(exception)
        }
      }

  /** Traverse all schemas to find if any of them cannot be added */
  def checkAllowance(schemas: List[SchemaFifth]) = {
    val result = schemas
      .foldLeft(List.empty[Either[String, SchemaFifth]]) { (accumulator, current) =>
        val SchemaFifth(map, schema, isPublic, createdAt, updatedAt) = current
        val successful = accumulator.map(_.toOption).unite
        isSchemaAllowed(successful, map, isPublic) match {
          case Right(_) => accumulator :+ Right(SchemaFifth(map, schema, isPublic, createdAt, updatedAt))
          case Left(error) => accumulator :+ Left(s"${map.schemaKey.toPath}: ${error.show}")
        }
      }

    result
      .parTraverse(_.toEitherNel)
      .void
      .leftMap(errors => s"Schemas not allowed: ${errors.toList.mkString(", ")}")

  }

  def isSchemaAllowed(previous: List[SchemaFifth], current: SchemaMap, isPublic: Boolean): Either[Inconsistency, Unit] = {
    val schemas = previous.filter(x => x.map.schemaKey.vendor == current.schemaKey.vendor && x.map.schemaKey.name == current.schemaKey.name)
    val previousPublic = schemas.forall(_.isPublic)
    val versions = schemas.map(_.map.schemaKey.version)
    if ((previousPublic && isPublic) || (!previousPublic && !isPublic) || schemas.isEmpty)
      VersionCursor.isAllowed(current.schemaKey.version, versions, patchesAllowed = false)
    else
      Inconsistency.Availability(isPublic, previousPublic).asLeft
  }

  def querySchemas =
    (fr"SELECT vendor, name, format, version, schema, createdat, updatedat, ispublic FROM" ++ OldSchemasTable ++ fr"WHERE draftnumber = '0' ORDER BY createdat")
      .query[(String, String, String, String, String, Instant, Instant, Boolean)]
      .map { case (vendor, name, format, version, body, createdAt, updatedAt, isPublic) =>
        val schemaMap = for {
          ver <- SchemaVer.parse(version)
          key <- SchemaKey.fromUri(s"iglu:$vendor/$name/$format/${ver.asString}")
        } yield SchemaMap(key)
        for {
          jsonBody <- parse(body).leftMap(_.show)
          map <- schemaMap.leftMap(_.code)
          schema <- SelfDescribingSchema.parse(jsonBody) match {
            case Left(ParseError.InvalidSchema) =>
              jsonBody.asRight  // Non self-describing JSON schema
            case Left(e) =>
              s"Invalid self-describing payload for [${map.schemaKey.toSchemaUri}], ${e.code}".asLeft
            case Right(schema) if schema.self == map =>
              schema.schema.asRight
            case Right(schema) =>
              s"Self-describing payload [${schema.self.schemaKey.toSchemaUri}] does not match its DB reference [${map.schemaKey.toSchemaUri}]".asLeft
          }
        } yield SchemaFifth(map, schema, isPublic, createdAt, updatedAt)
      }

  def migrateSchemas(schemas: List[SchemaFifth]) =
    for {
      row <- Stream.emits(schemas)
      _   <- Stream.eval_(addSchema(row.map, row.schema, row.isPublic, row.createdAt, row.updatedAt).run).void
    } yield ()

  def queryDrafts =
    (fr"SELECT vendor, name, format, draftnumber, schema, createdat, updatedat, ispublic FROM" ++ OldSchemasTable ++ fr"WHERE draftnumber != '0'")
      .query[(String, String, String, String, String, Instant, Instant, Boolean)]
      .map { case (vendor, name, format, draftId, body, createdAt, updatedAt, isPublic) =>
        for {
          verInt   <- Either.catchOnly[NumberFormatException](draftId.toInt).leftMap(_.getMessage)
          number   <- NonNegInt.from(verInt)
          jsonBody <- parse(body).leftMap(_.show)
          draftId   = SchemaDraft.DraftId(vendor, name, format, number)
          meta      = Schema.Metadata(createdAt, updatedAt, isPublic)
        } yield SchemaDraft(draftId, meta, jsonBody)
      }

  def migrateDrafts =
    for {
      row <- queryDrafts.stream
      _   <- row match {
        case Right(draft) =>
          Stream.eval_(addDraft(draft).run).void
        case Left(error) =>
          Stream.raiseError[ConnectionIO](IncompatibleStorage(error))
      }
    } yield ()

  def migrateKeys = {
    val query = (fr"SELECT uid, vendor_prefix, permission FROM" ++ OldPermissionsTable)
      .query[(UUID, String, String)]
      .map { case (id, prefix, perm) =>
        val vendor = Permission.Vendor.parse(prefix)
        val (schemaAction, keyAction) = perm match {
          case "super" => (Permission.Master.schema, Permission.Master.key)
          case "read" => (Some(Permission.SchemaAction.Read), Set.empty[Permission.KeyAction])
          case "write" => (Some(Permission.SchemaAction.CreateVendor), Set.empty[Permission.KeyAction])
          case _ => (Some(Permission.SchemaAction.Read), Set.empty[Permission.KeyAction]) // Should not happen
        }

        (id, Permission(vendor, schemaAction, keyAction))
      }

    query
      .stream
      .evalMap { case (id, permission) => Postgres.Sql.addPermission(id, permission).run }
      .void
  }

  def addSchema(schemaMap: SchemaMap, schema: Json, isPublic: Boolean, createdAt: Instant, updatedAt: Instant) = {
    val key = schemaMap.schemaKey
    val ver = key.version
    (fr"INSERT INTO" ++ Postgres.SchemasTable ++ fr"(vendor, name, format, model, revision, addition, created_at, updated_at, is_public, body)" ++
      fr"VALUES (${key.vendor}, ${key.name}, ${key.format}, ${ver.model}, ${ver.revision}, ${ver.addition}, $createdAt, $updatedAt, $isPublic, $schema)")
      .update
  }

  def addDraft(draft: SchemaDraft) =
    (fr"INSERT INTO" ++ Postgres.DraftsTable ++ fr"(vendor, name, format, version, created_at, updated_at, is_public, body)" ++
      fr"""VALUES (${draft.schemaMap.vendor}, ${draft.schemaMap.name}, ${draft.schemaMap.format},
        ${draft.schemaMap.version.value}, ${draft.metadata.createdAt}, ${draft.metadata.updatedAt},
        ${draft.metadata.isPublic}, ${draft.body})""")
      .update
}
