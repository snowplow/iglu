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
  val owner = "com.snowplowanalytics"
  val otherOwner = "com.unit"
  val otherOwner2 = "com.tdd"
  val faultyOwner = "com.benfradet"
  val permission = "write"
  val superPermission = "super"
  val isPublic = false
  val vendor = "com.snowplowanalytics.snowplow"
  val vendors = List(vendor)
  val otherVendor = "com.unittest"
  val otherVendor2 = "com.tdd.tdd"
  val otherVendors = List(otherVendor, otherVendor2)
  val faultyVendor = "com.snowplow"
  val faultyVendors = List(faultyVendor)
  val name = "ad_click"
  val names = List(name)
  val name2 = "ad_click2"
  val name2s = List(name2)
  val name3 = "ad_click3"
  val nameSelfDesc = "schema"
  val faultyName = "ad_click4"
  val faultyNames = List(faultyName)
  val format = "jsonschema"
  val notSupportedFormat = "notSupportedFormat"
  val formats = List(format)
  val version = "1-0-0"
  val versions = List(version)

  val invalidSchema = """{ "some" : "json" }"""
  val invalidSchema2 = """{ "some" : "json2" }"""
  val innerSchema = "\"some\" : \"json\""
  val innerSchema2 = "\"some\" : \"json2\""
  val validSchema = 
  """{
    "self": {
      "vendor": "com.snowplowanalytics.snowplow",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    }
  }"""
  val validSchema2 =
    """{
    "self": {
      "vendor": "com.tdd.tdd",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    }
  }"""
  val notJson = "not json"

  val validInstance = """{ "targetUrl": "somestr" }"""

  sequential

  "SchemaDAO" should {

    "for createTable" should {

      "create the schemas table" in {
        schema.createTable()
        schema.bootstrapSelfDescSchema()
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from pg_catalog.pg_tables
            where tablename = '${tableName}';""").first === 1
        }
      }

      "insert the self-desc validation schema" in {
        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where name = '${nameSelfDesc}';""").first === 1
        }
      }
    }

    "for add" should {

      "add a private schema properly" in {
        val (status, res) = schema.add(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        status === Created
        res must contain("Schema successfully added") and contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "add a public schema properly" in {
        val (status, res) = schema.add(otherVendor, name2, format, version,
          invalidSchema, otherOwner, permission, !isPublic)
        status === Created
        res must contain("Schema successfully added") and contain(otherVendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      "not add a schema if it already exists" in {
        val (status, res) = schema.add(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
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

      "add a private schema properly with super permission" in {
        val (status, res) = schema.add(otherVendor2, name, format, version,
          validSchema2, otherOwner2, superPermission, isPublic)
        status === Created
        res must contain("Schema successfully added") and contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from $tableName
            where vendor = '$otherVendor2' and
              name = '$name' and
              format = '$format' and
              version = '$version' and
              ispublic = false;""").first === 1
        }
      }
    }

    "for update" should {

      "update a schema properly" in {
        val (status, res) = schema.update(vendor, name, format, version,
          invalidSchema2, owner, permission)
        status === OK
        res must contain("Schema successfully updated") and contain(vendor)

        database withDynSession {
          Q.queryNA[String](
            s"""select schema
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}';""").first === invalidSchema2
        }
      }

      "create a schema if it does not exist" in {
        val (status, res) = schema.update(vendor, name3, format, version,
          invalidSchema, owner, permission)
        status === Created
        res must contain("Schema successfully added") and contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name3}' and
              format = '${format}' and
              version = '${version}';""").first === 1
        }
      }
    }

    "for get" should {

      "retrieve a schema properly if it is private" in {
        val (status, res) =
          schema.get(vendors, names, formats, versions, owner, permission)
        status === OK
        res must contain(innerSchema2)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve a schema properly if it is public" in {
        val (status, res) = schema.get(otherVendors, name2s, formats, versions,
          faultyOwner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.get(vendors, names, formats, versions, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema is not in the db" in {
        val (status, res) =
         schema.get(vendors, faultyNames, formats, versions, owner, permission)
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

      "retrieve metadata about a schema properly if it is private" in {
        val (status, res) = schema.getMetadata(vendors, names, formats,
          versions, owner, permission)
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
              version = '${version}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve metadata about a schema properly if it is public" in {
        val (status, res) = schema.getMetadata(otherVendors, name2s,
          formats, versions, faultyOwner, permission)
        status === OK
        res must contain(vendor) and contain(name2) and contain(format) and
          contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              format = '${format}' and
              version = '${version}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) = schema.getMetadata(vendors, names, formats,
          versions, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema is not in the db" in {
        val (status, res) = schema.getMetadata(vendors, faultyNames, formats,
          versions, owner, permission)
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

    "for getPublicSchemas" should {

      "retrieve every public schema available" in {
        val (status, res) = schema.getPublicSchemas(owner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where ispublic = true;""").first === 2
        }
      }
    }

    "for getPublicMetadata" should {
      
      "retrieve metadata about every public schema available" in {
        val (status, res) = schema.getPublicMetadata(owner, permission)
        status === OK
        res must contain(otherVendor) and contain(name2) and
          contain(format) and contain(version)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where ispublic = true;""").first === 2
        }
      }
    }

    "for getFromFormat" should {

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getFromFormat(vendors, names, formats, owner, permission)
        status === OK
        res must contain(innerSchema2)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getFromFormat(otherVendors, name2s, formats,
          faultyOwner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              format = '${format}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromFormat(vendors, names, formats, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getFromFormat(vendors, faultyNames, formats, owner, permission)
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

      "retrieve schemas properly if they are private" in {
        val (status, res) = schema.getMetadataFromFormat(vendors, names,
          formats, owner, permission)
        status === OK
        res must contain(vendor) and contain(name) and contain(format)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              format = '${format}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getMetadataFromFormat(otherVendors,
          name2s, formats, faultyOwner, permission)
        status === OK
        res must contain(vendor) and contain(name2) and contain(format)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              format = '${format}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) = schema.getMetadataFromFormat(vendors, names,
          formats, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) = schema.getMetadataFromFormat(vendors, faultyNames,
          formats, owner, permission)
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

      "retrieve schemas properly if they are private" in {
        val (status, res) =
          schema.getFromName(vendors, names, owner, permission)
        status === OK
        res must contain(innerSchema2)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getFromName(otherVendors, name2s, faultyOwner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromName(vendors, names, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getFromName(vendors, faultyNames, owner, permission)
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

      "retrieve schemas properly if they are private" in {
        val (status, res) =
          schema.getMetadataFromName(vendors, names, owner, permission)
        status === OK
        res must contain(vendor) and contain(name)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              name = '${name}' and
              ispublic = false;""").first === 1
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) = schema.getMetadataFromName(otherVendors, name2s,
          faultyOwner, permission)
        status === OK
        res must contain(otherVendor) and contain(name2)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              name = '${name2}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadataFromName(vendors, names, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getMetadataFromName(vendors, faultyNames, owner, permission)
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

      "return schemas properly if they are private" in {
        val (status, res) = schema.getFromVendor(vendors, owner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
              ispublic = false;""").first === 2
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getFromVendor(otherVendors, faultyOwner, permission)
        status === OK
        res must contain(innerSchema)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getFromVendor(vendors, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getFromVendor(faultyVendors, owner, permission)
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

      "return schemas properly if they are private" in {
        val (status, res) =
          schema.getMetadataFromVendor(vendors, owner, permission)
        status === OK
        res must contain(vendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${vendor}' and
            ispublic = false;""").first === 2
        }
      }

      "retrieve schemas properly if they are public" in {
        val (status, res) =
          schema.getMetadataFromVendor(otherVendors, faultyOwner, permission)
        status === OK
        res must contain(otherVendor)

        database withDynSession {
          Q.queryNA[Int](
            s"""select count(*)
            from ${tableName}
            where vendor = '${otherVendor}' and
              ispublic = true;""").first === 1
        }
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val (status, res) =
          schema.getMetadataFromVendor(vendors, faultyOwner, permission)
        status === Unauthorized
        res must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas matching the query" in {
        val (status, res) =
          schema.getMetadataFromVendor(faultyVendors, owner, permission)
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

      "return a 200 if the instance is valid against the schema" in {

        val name = "ad_click5"
        schema.add(vendor, name, format, version,
          """{
            "$schema": "http://com.snowplowanalytics/schema/jsonschema/1-0-0",
            "description": "Schema for an ad click event",
            "self": {
              "vendor": "com.snowplowanalytics.snowplow",
              "name": "ad_click",
              "format": "jsonschema",
              "version": "1-0-0"
            },
            "type": "object",
            "properties": {
              "clickId": {
                "type": "string"
              },
              "impressionId": {
                "type": "string"
              },
              "zoneId": {
                "type": "string"
              },
              "bannerId": {
                "type": "string"
              },
              "campaignId": {
                "type": "string"
              },
              "advertiserId": {
                "type": "string"
              },
              "targetUrl": {
                "type": "string",
                "minLength": 1
              },
              "costModel": {
                "enum": ["cpa", "cpc", "cpm"]
              },
              "cost": {
                "type": "number",
                "minimum": 0
              }
            },
            "required": ["targetUrl"],
            "additionalProperties": false
            }
          }""", owner, permission, isPublic)
        val (status, res) =
          schema.validate(vendor, name, format, version, validInstance)
        status === OK
        res must contain("The instance provided is valid against the schema")
      }

      "return a 400 if the instance is not valid against the schema" in {
        val (status, res) =
          schema.validate(vendor, "ad_click5", format, version, invalidSchema)
        status === BadRequest
        res must
          contain("The instance provided is not valid against the schema") and
          contain("report")
      }

      "return a 400 if the instance provided is not valid" in {
        val (status, res) = schema.validate(vendor, name, format, version,
          notJson)
        status === BadRequest
        res must contain("The instance provided is not valid")
      }

      "return a 404 if the schema is not found" in {
        val (status, res) = schema.validate(faultyVendor, name, format, version,
          validInstance)
        status === NotFound
        res must contain("The schema to validate against was not found")
      }
    }

    "for validateSchema" should {

      "return the schema if it is self-describing" in {

        schema.add("com.snowplowanalytics.self-desc", "schema", format, version,
          """{
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
          """, owner, permission, isPublic)

        val (status, res) = schema.validateSchema(validSchema, format)
        status === OK
        res must contain(validSchema)
      }

      "return a 200 if the schema provided is self-describing" in {
        val (status, res) = schema.validateSchema(validSchema, format, false)
        status === OK
        res must
          contain("The schema provided is a valid self-describing schema")
      }

      "return a 400 if the schema is not self-describing" in {
        val (status, res) = schema.validateSchema(invalidSchema, format)
        status === BadRequest
        res must contain("The schema provided is not a valid self-describing")
      }

      "return a 400 if the string provided is not valid" in {
        val (status, res) = schema.validateSchema(notJson, format)
        status === BadRequest
        res must contain("The schema provided is not valid")
      }

      "return a 400 if the schema format provided is not supported" in {
        val (status, res) =
          schema.validateSchema(validSchema, notSupportedFormat)
        status === BadRequest
        res must contain("The schema format provided is not supported")
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
