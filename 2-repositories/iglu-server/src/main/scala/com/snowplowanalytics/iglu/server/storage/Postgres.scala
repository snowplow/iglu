package com.snowplowanalytics.iglu.server
package storage

import java.util.UUID

import io.circe.Json

import cats.Monad
import cats.implicits._
import cats.effect.Clock

import fs2.Stream

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.postgres.circe.json.implicits._

import com.snowplowanalytics.iglu.core.{ SchemaMap, SchemaVer }
import com.snowplowanalytics.iglu.server.model.{ Permission, Schema, SchemaDraft }
import com.snowplowanalytics.iglu.server.model.SchemaDraft.DraftId

class Postgres[F[_]](xa: Transactor[F]) extends Storage[F] { self =>
  def getSchema(schemaMap: SchemaMap)(implicit F: Monad[F]): F[Option[Schema]] =
    Postgres.Sql.getSchema(schemaMap).option.transact(xa)

  def getPermission(apikey: UUID)(implicit F: Monad[F]): F[Option[Permission]] =
    Postgres.Sql.getPermission(apikey).option.transact(xa)

  def addSchema(schemaMap: SchemaMap, body: Json, isPublic: Boolean)(implicit C: Clock[F], M: Monad[F]): F[Unit] =
    Postgres.Sql.addSchema(schemaMap, body, isPublic).run.void.transact(xa)

  def getSchemas(implicit F: Monad[F]): Stream[F, Schema] =
    Postgres.Sql.getSchemas.stream.transact(xa)

  def getDraft(draftId: DraftId)(implicit F: Monad[F]): F[Option[SchemaDraft]] =
    Postgres.Sql.getDraft(draftId).option.transact(xa)

  def addDraft(draftId: DraftId, body: Json, isPublic: Boolean)(implicit C: Clock[F], M: Monad[F]): F[Unit] =
    Postgres.Sql.addDraft(draftId, body, isPublic).run.void.transact(xa)

  def getDrafts(implicit F: Monad[F]): Stream[F, SchemaDraft] =
    Postgres.Sql.getDrafts.stream.transact(xa)

  def addPermission(uuid: UUID, permission: Permission)(implicit F: Monad[F]): F[Unit] =
    Postgres.Sql.addPermission(uuid, permission).run.void.transact(xa)

  def deletePermission(id: UUID)(implicit F: Monad[F]): F[Unit] =
    Postgres.Sql.deletePermission(id)
      .run
      .void
      .transact(xa)

  def ping(implicit F: Monad[F]): F[Storage[F]] =
    sql"SELECT 42".query[Int].unique.transact(xa).as(self)

}

object Postgres {

  def apply[F[_]](xa: Transactor[F]): Postgres[F] = new Postgres(xa)

  def draftFr(id: DraftId): Fragment =
    fr"name = ${id.name}" ++
      fr" AND vendor = ${id.vendor}" ++
      fr" AND format = ${id.format}" ++
      fr" AND version = ${id.version.value}"

  def schemaMapFr(schemaMap: SchemaMap): Fragment =
    fr"name = ${schemaMap.schemaKey.name}" ++
      fr" AND vendor = ${schemaMap.schemaKey.vendor}" ++
      fr" AND format = ${schemaMap.schemaKey.format}" ++
      fr" AND " ++ schemaVerFr(schemaMap.schemaKey.version)

  def schemaVerFr(version: SchemaVer.Full): Fragment =
    fr"model = ${version.model} AND revision = ${version.revision} AND addition = ${version.addition}"

  object Sql {
    def getSchema(schemaMap: SchemaMap) =
      (sql"SELECT * FROM schemas WHERE " ++ schemaMapFr(schemaMap)).query[Schema]

    def getSchemas =
      sql"SELECT * FROM schemas".query[Schema]

    def addSchema(schemaMap: SchemaMap, schema: Json, isPublic: Boolean) = {
      val key = schemaMap.schemaKey
      val ver = key.version
      sql"""INSERT INTO schemas (vendor, name, format, model, revision, addition, created_at, updated_at, is_public, body)
        VALUES (${key.vendor}, ${key.name}, ${key.format}, ${ver.model}, ${ver.revision}, ${ver.addition}, current_timestamp, current_timestamp, $isPublic, $schema)"""
        .update
    }

    def getDraft(draftId: DraftId) =
      (sql"SELECT * FROM drafts WHERE " ++ draftFr(draftId)).query[SchemaDraft]

    def addDraft(id: DraftId, body: Json, isPublic: Boolean) =
      sql"""INSERT INTO drafts (vendor, name, format, version, created_at, updated_at, is_public, body)
        VALUES (${id.vendor}, ${id.name}, ${id.format}, ${id.version.value}, current_timestamp, current_timestamp, $isPublic, $body)""".stripMargin
        .update

    def getDrafts =
      sql"SELECT * FROM drafts".query[SchemaDraft]

    def getPermission(id: UUID) =
      sql"SELECT vendor, wildcard, schema_action::schema_action, key_action::key_action[] from permissions where apikey = $id".query[Permission]

    def addPermission(uuid: UUID, permission: Permission) = {
      val vendor = permission.vendor.parts.mkString(".")
      val keyActions = permission.key.toList.map(_.show)
      sql"""INSERT INTO permissions VALUES ($uuid, $vendor, ${permission.vendor.wildcard}, ${permission.schema}::schema_action, $keyActions::key_action[])""".update
    }

    def deletePermission(id: UUID) =
      sql"DELETE FROM permissions WHERE apikey = $id".update
  }

  object Bootstrap {
    val keyActionCreate =
      sql"""CREATE TYPE key_action AS ENUM ('CREATE', 'DELETE');"""
      .update
      .run

    val schemaActionCreate =
      sql"""CREATE TYPE schema_action AS ENUM ('READ', 'BUMP', 'CREATE', 'CREATE_VENDOR');"""
        .update
        .run

    val permissionsCreate = sql"""
      CREATE TABLE permissions (
        apikey              UUID            NOT NULL,
        vendor              VARCHAR(128),
        wildcard            BOOL            NOT NULL,
        schema_action       schema_action,
        key_action          key_action[]    NOT NULL,
        PRIMARY KEY (apikey)
      );"""
      .update
      .run

    val schemasCreate = sql"""
      CREATE TABLE schemas (
        vendor      VARCHAR(128)  NOT NULL,
        name        VARCHAR(128)  NOT NULL,
        format      VARCHAR(128)  NOT NULL,
        model       INTEGER       NOT NULL,
        revision    INTEGER       NOT NULL,
        addition    INTEGER       NOT NULL,

        created_at  TIMESTAMP     NOT NULL,
        updated_at  TIMESTAMP     NOT NULL,
        is_public   BOOLEAN       NOT NULL,

        body        JSON          NOT NULL
      );"""
      .update
      .run

    val draftsCreate = sql"""
      CREATE TABLE drafts (
        vendor      VARCHAR(128) NOT NULL,
        name        VARCHAR(128) NOT NULL,
        format      VARCHAR(128) NOT NULL,
        version     INTEGER      NOT NULL,

        created_at  TIMESTAMP    NOT NULL,
        updated_at  TIMESTAMP    NOT NULL,
        is_public   BOOLEAN      NOT NULL,

        body        JSON         NOT NULL
      );"""
      .update
      .run

    val allStatements =
      List(keyActionCreate, schemaActionCreate, permissionsCreate, schemasCreate, draftsCreate)

    def initialize[F[_]: Monad](xa: Transactor[F]) =
      allStatements
        .sequence[ConnectionIO, Int]
        .map(_.combineAll)
        .transact(xa)
  }
}
