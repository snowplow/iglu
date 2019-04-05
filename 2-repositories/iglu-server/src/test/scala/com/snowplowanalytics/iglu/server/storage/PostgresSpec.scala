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
package storage

import java.util.UUID
import io.circe.Json

import cats.syntax.apply._
import cats.syntax.traverse._
import cats.instances.list._
import cats.effect.IO

import doobie._
import doobie.specs2._
import doobie.implicits._

import eu.timepit.refined.types.numeric.NonNegInt

import com.snowplowanalytics.iglu.core.{SchemaMap, SchemaVer}
import com.snowplowanalytics.iglu.server.model.{ SchemaDraft, Permission }
import com.snowplowanalytics.iglu.server.migrations.Bootstrap

import scala.concurrent.ExecutionContext

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class PostgresSpec extends Specification with BeforeAll with IOChecker {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  val transactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/testdb", "postgres", "iglusecret"
  )


  def beforeAll(): Unit = {
    val dropStatement =
      List(
        fr"DROP TABLE IF EXISTS" ++ Postgres.PermissionsTable,
        fr"DROP TABLE IF EXISTS" ++ Postgres.SchemasTable,
        fr"DROP TABLE IF EXISTS" ++ Postgres.DraftsTable,
        fr"DROP TYPE IF EXISTS schema_action",
        fr"DROP TYPE IF EXISTS key_action")
      .map(_.update.run).sequence


    val action = dropStatement.transact(transactor) *>
      Bootstrap.initialize(transactor)

    action.unsafeRunSync()
    println(s"DB entities recreated")
  }

  val trivial = sql"SELECT 42".query[Int]

  "Postgres speciication" should {
    "check connection" in {
      check(trivial)
    }

    "typecheck getSchema" in {
      check(Postgres.Sql.getSchema(SchemaMap("does", "not", "exist", SchemaVer.Full(1, 0, 0))))
    }

    "typecheck addSchema" in {
      check(Postgres.Sql.addSchema(SchemaMap("does", "not", "exist", SchemaVer.Full(1, 0, 0)), Json.fromFields(List.empty), true))
    }

    "typecheck getDraft" in {
      check(Postgres.Sql.getDraft(SchemaDraft.DraftId("does", "not", "exist", NonNegInt(2))))
    }

    "typecheck getPermission" in {
      check(Postgres.Sql.getPermission(UUID.fromString("6907ba19-b6e0-4126-a931-dd236eec2736")))
    }

    "typecheck addPermission" in {
      check(Postgres.Sql.addPermission(UUID.fromString("6907ba19-b6e0-4126-a931-dd236eec2736"), Permission(Permission.Vendor(List("com", "acme"), false), None, Set.empty)))
    }

    "typecheck deletePermission" in {
      check(Postgres.Sql.deletePermission(UUID.fromString("6907ba19-b6e0-4126-a931-dd236eec2736")))
    }

    "typecheck getDrafts" in {
      check(Postgres.Sql.getDrafts)
    }
  }
}
