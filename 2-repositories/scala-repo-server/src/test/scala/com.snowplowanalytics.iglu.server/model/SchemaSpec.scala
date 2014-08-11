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

  val tableName = "schemas"
  val vendor = "com.unittest"
  val faultyVendor = "com.test"
  val name = "unit_test"
  val faultyName = "unit_test2"
  val format = "jsonschema"
  val version = "1-0-0"
  val schemaDef = """{ "some" : "json" }"""
  val innerSchema = """"some" : "json""""
  val validSchema = 
  """{
    "self": {
      "vendor": "com.snowplowanalytics.snowplow",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    }
  }"""
  val notJson = "not json"

  sequential

  "SchemaDAO" should {

    "for createTable" should {

      "create the schemas table" in {
        schema.createTable
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 1
        }
      }
    }

    "for add" should {

      "add a schema properly" in {
        val (status, res) = schema.add(vendor, name, format, version, schemaDef)
        status === Created
        res must contain("Schema added successfully") and contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }

      "not add a schema if it already exists" in {
        val (status, res) = schema.add(vendor, name, format, version, schemaDef)
        status === Unauthorized
        res must contain("This schema already exists")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }
    }

    "for get" should {

      "retrieve a schema properly" in {
        val (status, res) = schema.get(vendor, name, format, version)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) = schema.get(vendor, faultyName, format, version)
        status === NotFound
        res must contain("There are no schemas available here")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}' and
              version = '${version}';""").first === 0
        }
      }
    }

    "for getMetadata" should {

      "retrieve metadata about a schema properly" in {
        val (status, res) = schema.getMetadata(vendor, name, format, version)
        status === OK
        res must contain(vendor) and contain(name) and contain(format) and
          contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }

      "return not found if the schema is not in the db" in {
        val (status, res) =
          schema.getMetadata(vendor, faultyName, format, version)
        status === NotFound
        res must contain("There are no schemas available here")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}' and
              version = '${version}';""").first === 0
        }
      }
    }

    "for getFromFormat" should {

      "retrieve schemas properly" in {
        val (status, res) = schema.getFromFormat(vendor, name, format)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) = schema.getFromFormat(vendor, faultyName, format)
        status === NotFound
        res must contain("There are no schemas for this vendor, name, format")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}';""").first === 0
        }
      }
    }

    "for getMetadataFromFormat" should {

      "retrieve schemas properly" in {
        val (status, res) = schema.getMetadataFromFormat(vendor, name, format)
        status === OK
        res must contain(vendor) and contain(name) and contain(format)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) =
          schema.getMetadataFromFormat(vendor, faultyName, format)
        status === NotFound
        res must contain ("There are no schemas for this vendor, name, format")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}' and
              format = '${format}';""").first === 0
        }
      }
    }

    "for getFromName" should {

      "retrieve schemas properly" in {
        val (status, res) = schema.getFromName(vendor, name)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) = schema.getFromName(vendor, faultyName)
        status === NotFound
        res must contain("There are no schemas for this vendor, name")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}';""").first === 0
        }
      }
    }

    "for getMetadataFromName" should {

      "retrieve schemas properly" in {
        val (status, res) = schema.getMetadataFromName(vendor, name)
        status === OK
        res must contain(vendor) and contain(name)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) = schema.getMetadataFromName(vendor, faultyName)
        status === NotFound
        res must contain("There are no schemas for this vendor, name")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${faultyName}';""").first === 0
        }
      }
    }

    "for getFromVendor" should {

      "return schemas properly" in {
        val (status, res) = schema.getFromVendor(vendor)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) = schema.getFromVendor(faultyVendor)
        status === NotFound
        res must contain("There are no schemas for this vendor")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${faultyVendor}';""").first === 0
        }
      }
    }

    "for getMetadataFromVendor" should {

      "return schemas properly" in {
        val (status, res) = schema.getMetadataFromVendor(vendor)
        status === OK
        res must contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}';""").first === 1
        }
      }

      "return not found if there are no schemas matching the query" in {
        val (status, res) = schema.getMetadataFromVendor(faultyVendor)
        status === NotFound
        res must contain("There are no schemas for this vendor")

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${faultyVendor}';""").first === 0
        }
      }
    }

    "for validate" should {

      "return a json if it is self-describing" in {

        schema.add("com.snowplowanalytics.self-desc", "schema", "jsonschema",
          "1-0-0", """
          {
            "$schema": "http://json-schema.org/draft-04/schema#",
            "description": "Meta-schema for self-describing JSON schema",
            "self": {
              "vendor": "com.snowplowanalytics.self-desc",
              "name": "schema",
              "format": "jsonschema",
              "version": "1-0-0"
            },
            "allOf": [
            {
              "properties": {
                "self": {
                  "type": "object",
                  "properties": {
                    "vendor": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_.]+$"
                    },
                    "name": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_]+$"
                    },
                    "format": {
                      "type": "string",
                      "pattern": "^[a-zA-Z0-9-_]+$"
                    },
                    "version": {
                      "type": "string",
                      "pattern": "^[0-9]+-[0-9]+-[0-9]+$"
                    }
                  },
                  "required": [
                  "vendor",
                  "name",
                  "format",
                  "version"
                  ],
                  "additionalProperties": false
                }
              },
              "required": [
              "self"
              ]
            },
            {
              "$ref": "http://json-schema.org/draft-04/schema#"
            }
            ]
          }
          """)

        val (status, res) = schema.validate(validSchema)
        status === OK
        res must contain(validSchema)
      }

      "return bad request if the json is not self-describing" in {
        val (status, res) = schema.validate(schemaDef)
        status === BadRequest
        res must contain("The json provided is not a valid self-describing")
      }

      "return bad request if the string provided is not a json" in {
        val (status, res) = schema.validate(notJson)
        status === BadRequest
        res must contain("The json provided is not valid")
      }
    }

    "for dropTable" should {

      "drop the table properly" in {
        schema.dropTable

        database withDynSession {
          Q.queryNA[Int](
            """select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 0
        }
      }
    }
  }
}
