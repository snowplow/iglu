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
import cats.syntax.show._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.syntax.foldable._
import cats.instances.list._
import cats.instances.int._

import io.circe.Json
import io.circe.parser.parse

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.json.implicits._

import eu.timepit.refined.types.numeric.NonNegInt

import com.snowplowanalytics.iglu.core.{ SchemaMap, SchemaKey, SchemaVer, SelfDescribingSchema, ParseError }
import com.snowplowanalytics.iglu.core.circe.implicits._
import com.snowplowanalytics.iglu.server.model.VersionCursor

import model.{ Permission, SchemaDraft, Schema }
import storage.Postgres
import storage.Storage.IncompatibleStorage


/** Steps required to migrate the DB from pre-0.6.0 structure to current one */
object Fifth {

  val OldSchemasTable = Fragment.const("schemas")
  val OldPermissionsTable = Fragment.const("apikeys")

  def perform: ConnectionIO[Unit] =
    for {
      _ <- checkContent(querySchemas)
      _ <- checkConsistency(querySchemas)
      _ <- checkContent(queryDrafts)
      _ <- Bootstrap.allStatements.sequence[ConnectionIO, Int].map(_.combineAll)
      _ <- (migrateKeys ++ migrateSchemas ++ migrateDrafts).compile.drain
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

  def checkConsistency(query: Query0[Either[String, (SchemaMap, Json, Boolean, Instant, Instant)]]): ConnectionIO[Unit] = {
    val errors = query.stream
      .fold(List[Either[String, (SchemaMap, Json, Boolean, Instant, Instant)]]()) { (previous, current) =>
        current match {
          case Right((map, schema, isPublic, createdAt, updatedAt)) =>
            isSchemaAllowed(previous.flatMap(_.toOption), map, isPublic) match {
              case Right(_) => previous :+ Right((map, schema, isPublic, createdAt, updatedAt))
              case Left(error) => previous :+ Left(s"${map.schemaKey.toPath}: $error")
            }
          case Left(error) => previous :+ Left(error)
        }
      }

    errors.compile.toList.flatMap { list =>
      list.flatten.collect { case Left(error) => error } match {
        case Nil => Applicative[ConnectionIO].pure(())
        case err =>
          val exception = IncompatibleStorage(s"Inconsistent entities found: ${err.mkString(", ")}")
          MonadError[ConnectionIO, Throwable].raiseError(exception)
      }
    }
  }

  def isSchemaAllowed(previous: List[(SchemaMap, Json, Boolean, Instant, Instant)], current: SchemaMap, isPublic: Boolean): Either[String, Unit] = {
    val schemas = previous.filter(x => x._1.schemaKey.vendor == current.schemaKey.vendor && x._1.schemaKey.name == current.schemaKey.name)
    val previousPublic = schemas.forall(_._3)
    val versions = schemas.map(_._1.schemaKey.version)
    if ((previousPublic && isPublic) || (!previousPublic && !isPublic) || schemas.isEmpty)
      VersionCursor.isAllowed(current.schemaKey.version, versions, patchesAllowed = true).leftMap(_.show)
    else
      s"""Inconsistent schema availability. Cannot add ${if (isPublic) "public" else "private"} schema, previous versions are ${if (previousPublic) "public" else "private"}""".asLeft
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
        } yield (map, schema, isPublic, createdAt, updatedAt)
      }

  def migrateSchemas =
    for {
      row <- querySchemas.stream
      _   <- row match {
        case Right((map, body, isPublic, createdAt, updatedAt)) =>
          Stream.eval_(addSchema(map, body, isPublic, createdAt, updatedAt).run).void
        case Left(error) =>
          Stream.raiseError[ConnectionIO](IncompatibleStorage(error))
      }
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
