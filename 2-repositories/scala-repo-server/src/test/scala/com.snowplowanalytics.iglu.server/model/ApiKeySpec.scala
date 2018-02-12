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
package model

// This project
import util.IgluPostgresDriver.simple._

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

// Akka Http
import akka.http.scaladsl.model.StatusCodes._

class ApiKeySpec extends Specification with SetupAndDestroy {

  val apiKey = new ApiKeyDAO(database)

  implicit val formats = DefaultFormats

  val tableName = "apikeys"
  val vendorPrefix = "com.unittest"
  val faultyVendorPrefix = "com.unit"
  val notUid = "this-is-not-an-uuid"

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

      "add the API keys properly" in {
        val (status, res) = apiKey.addReadWrite(vendorPrefix)
        val map = parse(res).extract[Map[String, String]]

        readKey = map getOrElse("read", "")
        writeKey = map getOrElse("write", "")

        status === Created
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
            where vendor_prefix = '${vendorPrefix}';""").first === 2
        }
      }

      "not add API keys if the vendor prefix is conflicting with an existing" +
      "one" in {
        val (status, res) = apiKey.addReadWrite(faultyVendorPrefix)
        status === Unauthorized
        res must
          contain("This vendor prefix is conflicting with an existing one")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor_prefix = '${faultyVendorPrefix}';""").first === 0
        }
      }
    }

    "for get" should {

      "properly retrieve the API key" in {
        apiKey.get(readKey) match {
          case Some((vendorPrefix, permission)) =>
            vendorPrefix must contain(vendorPrefix)
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

      "return None if the API key is not in the db" in {
        val uid = UUID.randomUUID.toString
        apiKey.get(uid) match {
          case Some(("-",  "-")) => success
          case _ => failure
        }

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${uid}';""").first === 0
        }
      }

      "return None if the API key is not a uuid" in {
        apiKey.get(notUid) match {
          case Some(("-",  "-")) => success
          case _ => failure
        }
      }
    }

    "for delete" should {

      "properly delete an API key" in {
        val (status, res) = apiKey.delete(readKey)
        status === OK
        res must contain("API key successfully deleted")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 0
        }
      }

      "return not found if the API key is not in the database" in {
        val (status, res) = apiKey.delete(readKey)
        status === NotFound
        res must contain("API key not found")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where uid = '${readKey}';""").first === 0
        }
      }

      "return a 401 if the key is not an uuid" in {
        val (status, res) = apiKey.delete(notUid)
        status === Unauthorized
        res must contain("The API key provided is not an UUID")
      }
    }

    "for deleteFromVendorPrefix" should {

      "properly delete API keys associated with a vendor prefix" in {
        val (status, res) = apiKey.deleteFromVendorPrefix(vendorPrefix)
        status === OK
        res must contain("API key deleted for " + vendorPrefix)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor_prefix = '${vendorPrefix}';""").first === 0
        }
      }

      "return a 404 if there are no API keys associated with this vendor" +
      "prefix" in {
        val (status, res) = apiKey.deleteFromVendorPrefix(vendorPrefix)
        status === NotFound
        res must contain("Vendor prefix not found")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor_prefix = '${vendorPrefix}';""").first === 0
        }
      }
    }
  }
}
