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
//import util.IgluPostgresDriver.jsonMethods._
import validation.ValidatableJsonMethods._

// Jackson
import com.fasterxml.jackson.databind.JsonNode

// Joda
import org.joda.time.LocalDateTime

// Json4s
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.writePretty

// Json schema
import com.github.fge.jsonschema.SchemaVersion
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.main.{
  JsonSchemaFactory,
  JsonValidator
}
import com.github.fge.jsonschema.core.report.{
  ListReportProvider,
  ProcessingMessage,
  LogLevel
}

// Scala
import scala.collection.JavaConversions._

// Slick
import Database.dynamicSession

//Spray
import spray.http.StatusCode
import spray.http.StatusCodes._

/**
 * DAO for accessiing the schemas table in the database
 * @constructor create a schema DAO with a reference to the database
 * @param db a reference to a ``Database``
 */
class SchemaDAO(val db: Database) extends DAO {

  /**
   * Case class representing a schema in the database.
   * @constructor create a schema from required data
   * @param schemaId primary key
   * @param vendor the scehma's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @param schema the json forming the schema
   * @param createdAt data at which point the schema was created
   */
  case class Schema(
    schemaId: Int,
    vendor: String,
    name: String,
    format: String,
    version: String,
    schema: String,
    //schema: JValue,
    createdAt: LocalDateTime
  )

  /**
   * Schema for the schemas table
   */
  class Schemas(tag: Tag) extends Table[Schema](tag, "schemas") {
    def schemaId = column[Int](
      "schemaid", O.AutoInc, O.PrimaryKey, O.DBType("serial"))
    def vendor = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def name = column[String]("name", O.DBType("varchar(50)"), O.NotNull)
    def format = column[String]("format", O.DBType("varchar(50)"), O.NotNull)
    def version = column[String]("version", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[String]("schema", O.DBType("text"), O.NotNull)
    //def schema = column[JValue]("schema", O.DBType("json"), O.NotNull)
    def createdAt = column[LocalDateTime]("createdat", O.DBType("timestamp"),
      O.NotNull)

    def * = (schemaId, vendor, name, format, version, schema, createdAt) <>
      (Schema.tupled, Schema.unapply)
  }

  //Object used to access the table
  val schemas = TableQuery[Schemas]

  //Case classes for json formatting
  case class ResSchema(schema: JValue, createdAt: String)
  case class ResSchemaFormat(schema: JValue, version: String,
    createdAt: String)
  case class ResSchemaName(schema: JValue, format: String,
    version: String, createdAt: String)
  case class ResSchemaVendor(schema: JValue, name: String, format: String,
    version: String, createdAt: String)
  case class ResMetadata(vendor: String, name: String, format: String,
    version: String, createdAt: String)

  /**
   * Creates the schemas table.
   */
  def createTable = db withDynSession { schemas.ddl.create }

  /**
   * Deletes the schemas table.
   */
  def dropTable = db withDynSession { schemas.ddl.drop }

  /**
   * Gets every schema belongig to a specific vendor.
   * @param vendor schemas' vendor
   * @return a status code and json containing the list of all schemas
   * of this vendor pair
   */
  def getFromVendor(vendor: String): (StatusCode, String) =
    db withDynSession {
      val l: List[ResSchemaVendor] =
        schemas.filter(_.vendor === vendor).
          map(s => (s.schema, s.name, s.format, s.version, s.createdAt)).list.
          map(s => ResSchemaVendor(parse(s._1), s._2, s._3, s._4,
            s._5.toString("MM/dd/yyyy HH:mm:ss")))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no schemas for this vendor"))
      }
    }

  /**
   * Get metadata about every schemas belonging to a specific vendor.
   * @param vendor schemas' vendor
   * @return a status code and json containing metadata about every schema
   * of this vendor pair
   */
  def getMetadataFromVendor(vendor: String): (StatusCode, String) =
    db withDynSession {
      val l: List[ResMetadata] =
        schemas.filter(_.vendor === vendor).
        map(s => (s.vendor, s.name, s.format, s.version, s.createdAt)).list.
        map(s => ResMetadata(s._1, s._2, s._3, s._4,
          s._5.toString("MM/dd/yyyy HH:mm:ss")))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no schemas for this vendor"))
      }
    }

  /**
   * Gets every schemas for this specific vendor, name combination.
   * @param vendor schemas' vendor
   * @param name schemas' naeme
   * @return a status code and json containing the list of all schemas
   * satifsfying the query pair
   */
  def getFromName(vendor: String, name: String): (StatusCode, String) =
    db withDynSession {
      val l: List[ResSchemaName] =
        schemas.filter(s => s.vendor === vendor && s.name === name).
          map(s => (s.schema, s.format, s.version, s.createdAt)).list.
          map(s => ResSchemaName(parse(s._1), s._2, s._3,
            s._4.toString("MM/dd/yyyy HH:mm:ss")))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404,
          "There are no schemas for this vendor, name combination"))
      }
    }

  /**
   * Gets metadata about every schemas for this specific venodr, name
   * combination.
   * @param vendor schemas' vendor
   * @param name schemas' name
   * @return a status code and json containing metadata about the schemas
   * satifsfying the query pair
   */
  def getMetadataFromName(vendor: String, name: String): (StatusCode, String) =
    db withDynSession {
      val l: List[ResMetadata] =
        schemas.filter(s =>
          s.vendor === vendor &&
          s.name === name).
        map(s => (s.vendor, s.name, s.format, s.version, s.createdAt)).list.
        map(s => ResMetadata(s._1, s._2, s._3, s._4,
          s._5.toString("MM/dd/yyyy HH:mm:ss")))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404,
          "There are no schemas for this vendor, name combination"))
      }
    }

  /**
   * Retrieves every version of a schema.
   * @param vendor schemas' vendor
   * @param name schenas' name
   * @param format schemas' format
   * @return a status code and json containing the list of every version of a
   * specific schema pair
   */
  def getFromFormat(vendor: String, name: String, format: String):
    (StatusCode, String) =
      db withDynSession {
        val l: List[ResSchemaFormat] =
          schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format).
          map(s => (s.schema, s.version, s.createdAt)).list.
          map(s => ResSchemaFormat(parse(s._1), s._2,
            s._3.toString("MM/dd/yyyy HH:mm:ss")))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404,
            "There are no schemas for this vendor, name, format combination"))
        }
      }

  /**
   * Gets metadata about every version of a schema.
   * @param vendor schemas' vendor
   * @param name schemas' name
   * @param format schemas' format
   * @return a status code and json containing metadata about every version of a
   * specific schema pair
   */
  def getMetadataFromFormat(vendor: String, name: String, format: String):
    (StatusCode, String) =
      db withDynSession {
        val l: List[ResMetadata] = schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format).
          map(s => (s.vendor, s.name, s.format, s.version, s.createdAt)).list.
          map(s => ResMetadata(s._1, s._2, s._3, s._4,
            s._5.toString("MM/dd/yyyy HH:mm:ss")))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404,
            "There are no schemas for this vendor, name, format, combination"))
        }
      }

  /**
   * Gets a single schema specifying all its characteristics.
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @return a status code and json containing the schema pair
   */
  def get(vendor: String, name: String, format: String, version: String):
    (StatusCode, String) =
      db withDynSession {
        val l: List[ResSchema] = schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format &&
            s.version === version).
          map(s => (s.schema, s.createdAt)).list.
          map(s => ResSchema(parse(s._1),
            s._2.toString("MM/dd/yyyy HH:mm:ss")))

        if (l.length == 1) {
          (OK, writePretty(l(0)))
        } else {
          (NotFound, result(404, "There are no schemas available here"))
        }
      }

  /**
   * Gets only metadata about the schema: its vendor, name, format and version.
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schea's format
   * @param version the schema's version
   * @return a status code and json containing the metadata pair
   */
  def getMetadata(vendor: String, name: String, format: String,
    version: String): (StatusCode, String) =
      db withDynSession {
        val l = schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format &&
            s.version === version).
          map(s => (s.vendor, s.name, s.format, s.version, s.createdAt)).list.
          map(s => ResMetadata(s._1, s._2, s._3, s._4,
            s._5.toString("MM/dd/yyyy HH:mm:ss")))

        if (l.length == 1) {
          (OK, writePretty(l(0)))
        } else {
          (NotFound, result(404, "There are no schemas available here"))
        }
      }

  /**
   * Gets a single schema without the metadata associated
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @return the schema without metadata
   */
  private def getNoMetadata(vendor: String, name: String, format: String,
    version: String): String =
      db withDynSession {
        val l: List[String] = schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format &&
            s.version === version).
          map(_.schema).list

        l(0)
      }

  /**
   * Adds a schema after validating it does not already exist.
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @param schema the schema itself
   * @return a status code json response pair
   */
  def add(vendor: String, name: String, format: String, version: String,
    schema: String): (StatusCode, String) =
      db withDynSession {
        get(vendor, name, format, version) match {
          case (OK, j) => (Unauthorized,
            result(401, "This schema already exists"))
          case c => schemas.insert(
            Schema(0, vendor, name, format, version, schema,
              new LocalDateTime())) match {
                case 0 => (InternalServerError,
                  result(500, "Something went wrong"))
                case n => (OK, result(200, "Schema added successfully"))
              }
        }
      }

  /**
   * Validates that the json schema provided is a well-formatted json and
   * is self-describing.
   * @param json the json to be validated
   * @return a status code and json response pair
   */
  def validate(json: String): (StatusCode, String) =
    parseOpt(json) match {
      case Some(jvalue) => {
        val jsonNode = asJsonNode(jvalue)
        val schemaNode = asJsonNode(parse(getNoMetadata(
          "com.snowplowanalytics.self-desc", "schema", "jsonschema", "1-0-0")))

        validateAgainstSchema(jsonNode, schemaNode) match {
          case Some(j) => (OK, json)
          case _ => (BadRequest, result(400,
            "The json provided is not a valid self-describing json"))
        }
      }
      case None => (BadRequest, result(400, "The json provided is not valid"))
    }
}
