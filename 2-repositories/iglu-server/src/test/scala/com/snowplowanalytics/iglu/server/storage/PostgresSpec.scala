package com.snowplowanalytics.iglu.server
package storage

import java.util.UUID
import io.circe.Json

import cats.syntax.apply._
import cats.effect.IO

import doobie._
import doobie.specs2._
import doobie.implicits._
import eu.timepit.refined.types.numeric.NonNegInt

import com.snowplowanalytics.iglu.core.{SchemaMap, SchemaVer}
import com.snowplowanalytics.iglu.server.model.SchemaDraft
import com.snowplowanalytics.iglu.server.model.Permission

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
      sql"""DROP TABLE IF EXISTS permissions;
           |DROP TABLE IF EXISTS schemas;
           |DROP TABLE IF EXISTS drafts;
           |DROP TYPE IF EXISTS schema_action;
           |DROP TYPE IF EXISTS key_action;""".stripMargin
    val action = dropStatement.update.run.transact(transactor) *>
      Postgres.Bootstrap.initialize(transactor)

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
