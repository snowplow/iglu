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

// Scalaz
import scalaz._
import Scalaz._

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
  case class Metadata(location: String, createdAt: String)
  case class ResSchema(schema: JValue, metadata: Metadata)
  case class ResMetadata(vendor: String, name: String, format: String,
    version: String, metadata: Metadata)

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
   * @param vendors schemas' vendors
   * @return a status code and json pair containing the list of all schemas
   * of this vendor
   */
  def getFromVendor(vendors: List[String]): (StatusCode, String) =
    db withDynSession {
      val l: List[ResSchema] =
        (for {
          s <- schemas if s.vendor inSet vendors
        } yield s)
          .list
          .map(s => ResSchema(parse(s.schema),
            Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
            s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no schemas for this vendor"))
      }
    }

  /**
   * Gets metadata about every schemas belonging to a specific vendor.
   * @param vendors schemas' vendors
   * @return a status code and json pair containing metadata about every schema
   * of this vendor
   */
  def getMetadataFromVendor(vendors: List[String]): (StatusCode, String) =
    db withDynSession {
      val l: List[ResMetadata] =
        (for {
          s <- schemas if s.vendor inSet vendors
        } yield s)
          .list
          .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
            Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
            s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no schemas for this vendor"))
      }
    }

  /**
   * Gets every schemas for this vendor, names combination.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @return a status code and json pair containing the list of all schemas
   * satifsfying the query
   */
  def getFromName(vendors: List[String], names: List[String]): (StatusCode, String) =
    db withDynSession {
      val l: List[ResSchema] =
        (for {
          s <- schemas if (s.vendor inSet vendors) &&
            (s.name inSet names)
        } yield s)
        .list
        .map(s => ResSchema(parse(s.schema),
          Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
          s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

      if (l.length > 0) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404,
          "There are no schemas for this vendor, name combination"))
      }
    }

  /**
   * Gets metadata about every schemas for this vendor, names combination.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @return a status code and json pair containing metadata about the schemas
   * satifsfying the query
   */
  def getMetadataFromName(vendors: List[String], names: List[String]):
    (StatusCode, String) =
      db withDynSession {
        val l: List[ResMetadata] =
          (for {
            s <- schemas if (s.vendor inSet vendors) &&
              (s.name inSet names)
          } yield s)
            .list
            .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
              s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404,
            "There are no schemas for this vendor, name combination"))
        }
      }

  /**
   * Retrieves every version of a schema.
   * @param vendors schemas' vendors
   * @param names schenas' names
   * @param schemaFormats schemas' formats
   * @return a status code and json pair containing the list of every version of
   * a schema
   */
  def getFromFormat(vendors: List[String], names: List[String],
    schemaFormats: List[String]): (StatusCode, String) =
      db withDynSession {
        val l: List[ResSchema] =
          (for {
            s <- schemas if (s.vendor inSet vendors) &&
              (s.name inSet names) &&
              (s.format inSet schemaFormats)
          } yield s)
            .list
            .map(s => ResSchema(parse(s.schema),
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
              s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404,
            "There are no schemas for this vendor, name, format combination"))
        }
      }

  /**
   * Gets metadata about every version of a schema.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @param schemaFormats schemas' formats
   * @return a status code and json pair containing metadata about every version
   * of a schema
   */
  def getMetadataFromFormat(vendors: List[String], names: List[String],
    schemaFormats: List[String]): (StatusCode, String) =
      db withDynSession {
        val l: List[ResMetadata] =
          (for {
            s <- schemas if (s.vendor inSet vendors) &&
              (s.name inSet names) &&
              (s.format inSet schemaFormats)
          } yield s)
            .list
            .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
              s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404,
            "There are no schemas for this vendor, name, format, combination"))
        }
      }

  /**
   * Gets a single schema specifying all its characteristics.
   * @param vendors the schema's vendors
   * @param names the schema's names
   * @param schemaFormats the schema's formats
   * @param versions the schema's versions
   * @return a status code and json pair containing the schema
   */
  def get(vendors: List[String], names: List[String], schemaFormats: List[String],
    versions: List[String]): (StatusCode, String) =
      db withDynSession {
        val l: List[ResSchema] =
          (for {
            s <- schemas if (s.vendor inSet vendors) &&
              (s.name inSet names) &&
              (s.format inSet schemaFormats) &&
              (s.version inSet versions)
          } yield s)
            .list
            .map(s => ResSchema(parse(s.schema),
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
              s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

        if (l.length > 0) {
          (OK, writePretty(l))
        } else {
          (NotFound, result(404, "There are no schemas available here"))
        }
      }

  /**
   * Gets only metadata about the schema: its vendor, name, format and version.
   * @param vendors the schema's vendors
   * @param names the schema's names
   * @param schemaFormats the schea's formats
   * @param versions the schema's versions
   * @return a status code and json pair containing the metadata
   */
  def getMetadata(vendors: List[String], names: List[String],
    schemaFormats: List[String], versions: List[String]): (StatusCode, String) =
      db withDynSession {
        val l: List[ResMetadata] =
          (for {
            s <- schemas if (s.vendor inSet vendors) &&
              (s.name inSet names) &&
              (s.format inSet schemaFormats) &&
              (s.version inSet versions)
          } yield s)
            .list
            .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
              s.createdAt.toString("MM/dd/yyyy HH:mm:ss"))))

        if (l.length > 0) {
          (OK, writePretty(l))
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
        get(List(vendor), List(name), List(format), List(version)) match {
          case (OK, j) => (Unauthorized,
            result(401, "This schema already exists"))
          case c => schemas.insert(
            Schema(0, vendor, name, format, version, schema,
              new LocalDateTime())) match {
                case 0 => (InternalServerError,
                  result(500, "Something went wrong"))
                case n => (Created, result(201, "Schema added successfully",
                  buildLoc(vendor, name, format, version)))
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
          case scalaz.Success(j) => (OK, json)
          case Failure(l) => (BadRequest,
            result(400,
              "The json provided is not a valid self-describing json",
              fromJsonNode(l.head.asJson)))
        }
      }
      case None => (BadRequest, result(400, "The json provided is not valid"))
    }

  /**
   * Helper method to build the location of the schema from its metadata.
   * @param vendor of the schema
   * @param name of the schema
   * @param format of the schema
   * @param version of the schema
   * @return the location of the schema
   */
  private def buildLoc(vendor: String, name: String, format: String,
    version: String): String =
      List("", "api", "schemas", vendor, name, format, version) mkString("/")
}
