/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server
package test.model

// This project
import model.ApiKeyDAO
import util.IgluPostgresDriver.simple._
import util.Config

// Java
import java.util.UUID

// Json4s
import org.json4s._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

// Slick
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }

// Specs2
import org.specs2.mutable.Specification

// Spray
import spray.http.StatusCodes._

class ApiKeySpec extends Specification with SetupAndDestroy {

  val apiKey = new ApiKeyDAO(database)

  implicit val formats = DefaultFormats

  val tableName = "apikeys"
  val owner = "com.unittest"
  val faultyOwner = "com.unit"

  var readKey = ""
  var writeKey = ""

  sequential

  "ApiKeyDAO" should {

    "for createTable" should {

      "create the apikeys table" in {
        apiKey.createTable
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 1
        }
      }
    }

    "for addReadWrite" should {

      "add the api keys properly" in {
        val (status, res) = apiKey.addReadWrite(owner)
        val map = parse(res).extract[Map[String, String]]
        readKey = map getOrElse("read", "")
        writeKey = map getOrElse("write", "")

        status === OK
        res must contain("read") and contain("write")
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 1
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${writeKey}';""").first === 1
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${owner}';""").first === 2
        }
      }

      "not add api keys if the owner is conflicting with an existing one" in {
        val (status, res) = apiKey.addReadWrite(faultyOwner)
        status === Unauthorized
        res must contain("This vendor is conflicting with an existing one")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${faultyOwner}';""").first === 0
        }
      }
    }

    "for get" should {

      "properly retrieve the api key" in {
        apiKey.get(readKey) match {
          case Some((owner, permission)) =>
            owner must contain(owner)
            permission must contain("read")
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 1
        }
      }

      "return None if the api key is not in the db" in {
        val uid = UUID.randomUUID.toString
        apiKey.get(uid) match {
          case None => success
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${uid}';""").first === 0
        }
      }

      "return None if the api key is not a uuid" in {
        val notUid = "this-is-not-an-uuid"
        apiKey.get(notUid) match {
          case None => success
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${notUid}';""").first === 0
        }
      }
    }

    "for delete" should {

      "properly delete an api key" in {
        val (status, res) = apiKey.delete(UUID.fromString(readKey))
        val (status2, res2) = apiKey.delete(UUID.fromString(writeKey))

        status === OK
        res must contain("Api key successfully deleted")
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 0
        }

        status2 === OK
        res2 must contain("Api key successfully deleted")
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${writeKey}';""").first === 0
        }
      }

      "return not found if the api key is not in the database" in {
        val (status, res) = apiKey.delete(UUID.fromString(readKey))
        status === NotFound
        res must contain("Api key not found")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 0
        }
      }
    }
  }
}
