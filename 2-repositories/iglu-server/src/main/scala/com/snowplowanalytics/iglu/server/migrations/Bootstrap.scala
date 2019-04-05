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
package com.snowplowanalytics.iglu.server.migrations

import cats.Monad
import cats.implicits._

import doobie._
import doobie.implicits._

import com.snowplowanalytics.iglu.server.storage.Postgres


object Bootstrap {
  val keyActionCreate =
    sql"""CREATE TYPE key_action AS ENUM ('CREATE', 'DELETE')"""
      .update
      .run

  val schemaActionCreate =
    sql"""CREATE TYPE schema_action AS ENUM ('READ', 'BUMP', 'CREATE', 'CREATE_VENDOR')"""
      .update
      .run

  val permissionsCreate = (
    fr"CREATE TABLE" ++ Postgres.PermissionsTable ++ fr"""(
        apikey              UUID            NOT NULL,
        vendor              VARCHAR(128),
        wildcard            BOOL            NOT NULL,
        schema_action       schema_action,
        key_action          key_action[]    NOT NULL,
        PRIMARY KEY (apikey)
      );""")
    .update
    .run

  val schemasCreate = (
    fr"CREATE TABLE" ++ Postgres.SchemasTable ++ fr"""(
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
      )""")
    .update
    .run

  val draftsCreate = (
    fr"CREATE TABLE" ++ Postgres.DraftsTable ++ fr"""(
        vendor      VARCHAR(128) NOT NULL,
        name        VARCHAR(128) NOT NULL,
        format      VARCHAR(128) NOT NULL,
        version     INTEGER      NOT NULL,

        created_at  TIMESTAMP    NOT NULL,
        updated_at  TIMESTAMP    NOT NULL,
        is_public   BOOLEAN      NOT NULL,

        body        JSON         NOT NULL
      )""")
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
