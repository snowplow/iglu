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

// Slick
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }

// Specs2
import org.specs2.mutable.Specification

// Spray
import spray.json._
import DefaultJsonProtocol._
import spray.http.StatusCodes._

class ApiKeySpec extends Specification with SetupAndDestroy {

  val apiKey = new ApiKeyDAO(database)

  var readKey = ""
  var writeKey = ""

  sequential

  "ApiKeyDAO" should {

    "for createTable" should {

      "create the apikeys table" in {
        apiKey.createTable
        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from pg_catalog.pg_tables
            where tablename = 'apikeys';""").first === 1
        }
      }
    }

    "for addReadWrite" should {

      "add the api keys properly" in {
        val (status, res) = apiKey.addReadWrite("com.unittest")
        val map = res.parseJson.convertTo[Map[String, String]]
        readKey = map getOrElse("read", "")
        writeKey = map getOrElse("write", "")

        status === OK
        res must contain("read") and contain("write")
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from apikeys
            where uid = '${readKey}';""").first === 1
          Q.queryNA[Int](
            s"""select count(*)
            from apikeys
            where uid = '${writeKey}';""").first === 1
        }
      }

      "not add api keys if the owner is conflicting with an existing one" in {
        val (status, res) = apiKey.addReadWrite("com.unit")
        status === Unauthorized
        res must contain("This vendor is conflicting with an existing one")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from apikeys
            where vendor = 'com.unit';""").first === 0
        }
      }
    }

    "for get" should {

      "properly retrieve the api key" in {
        apiKey.get(UUID.fromString(readKey)) match {
          case Some((owner, permission)) =>
            owner must contain("com.unittest")
            permission must contain("read")
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from apikeys
            where uid = '${readKey}';""").first === 1
        }
      }

      "return None if the api key is not in the db" in {
        val uid = UUID.randomUUID
        apiKey.get(uid) match {
          case None => success
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from apikeys
            where uid = '${uid.toString}';""").first === 0
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
            from apikeys
            where uid = '${readKey}';""").first === 0
        }

        status2 === OK
        res2 must contain("Api key successfully deleted")
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from apikeys
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
            from apikeys
            where uid = '${readKey}';""").first === 0
        }
      }
    }
  }
}
