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
import model.SchemaDAO
import util.IgluPostgresDriver.simple._
import util.Config

// Slick
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }

// Specs2
import org.specs2.mutable.Specification

// Spray
import spray.http.StatusCodes._

class SchemaSpec extends Specification with SetupAndDestroy {

  val schema = new SchemaDAO(database)

  sequential

  "SchemaDAO" should {

    "for createTable" should {

      "create the schemas table" in {
        schema.createTable
        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from pg_catalog.pg_tables
            where tablename = 'schemas';""").first === 1
        }
      }
    }

    "for add" should {

      "add a schema properly" in {
        val (status, res) = schema.add("com.benfradet", "unit_test",
          "jsonschema", "1-0-0", """{ "some": "json" }""")
        status === OK
        res must contain("Schema added successfully")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test' and
              format = 'jsonschema' and
              version = '1-0-0';""").first === 1
        }
      }

      "not add a schema if it already exists" in {
        val (status, res) = schema.add("com.benfradet", "unit_test",
          "jsonschema", "1-0-0", """"{ "some": "json" }""")
        status must be(Unauthorized)
        res must contain("This schema already exists")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test' and
              format = 'jsonschema' and
              version = '1-0-0';""").first === 1
        }
      }
    }

    "for get" should {

      "retrieve a schema properly" in {
        val (status, res) = schema.get("com.benfradet", "unit_test",
          "jsonschema", "1-0-0")
        status === OK
        res must contain(""""some": "json"""")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test' and
              format = 'jsonschema' and
              version = '1-0-0';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) = schema.get("com.benfradet", "unit_test2",
          "jsonschema", "1-0-0")
        status === NotFound
        res must contain("There are no schemas available here")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
            name = 'unit_test2' and
            format = 'jsonschema' and
            version = '1-0-0';""").first === 0
        }
      }
    }

    "for getFromFormat" should {

      "retrieve a schema properly" in {
        val (status, res) = schema.getFromFormat("com.benfradet",
          "unit_test", "jsonschema")
        status === OK
        res must contain(""""some": "json"""")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test' and
              format = 'jsonschema';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) = schema.getFromFormat("com.benfradet",
          "unit_test2", "jsonschema")
        status === NotFound
        res must contain("There are no schemas for this vendor, name, format")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test2' and
              format = 'jsonschema';""").first === 0
        }
      }
    }

    "for getFromName" should {

      "retrieve a schema properly" in {
        val (status, res) = schema.getFromName("com.benfradet", "unit_test")
        status === OK
        res must contain(""""some": "json"""")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) = schema.getFromName("com.benfradet", "unit_test2")
        status === NotFound
        res must contain("There are no schemas for this vendor, name")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet' and
              name = 'unit_test2';""").first === 0
        }
      }
    }

    "for getFromVendor" should {

      "return a schema properly" in {
        val (status, res) = schema.getFromVendor("com.benfradet")
        status === OK
        res must contain(""""some": "json"""")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.benfradet';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) = schema.getFromVendor("com.ben")
        status === NotFound
        res must contain("There are no schemas for this vendor")

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from schemas
            where vendor = 'com.ben';""").first === 0
        }
      }
    }

    "for dropTable" should {

      "drop the table properly" in {
        schema.dropTable

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from pg_catalog.pg_tables
            where tablename = 'schemas';""").first === 0
        }
      }
    }
  }
}
