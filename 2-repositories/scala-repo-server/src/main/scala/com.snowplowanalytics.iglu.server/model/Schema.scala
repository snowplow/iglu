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
import org.json4s.Extraction
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
import scala.io.Source

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
   * @param schema the schema
   * @param createdAt data at which point the schema was created
   */
  case class Schema(
    schemaId: Int,
    vendor: String,
    name: String,
    format: String,
    version: String,
    schema: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    isPublic: Boolean
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
    def createdAt = column[LocalDateTime]("createdat", O.DBType("timestamp"),
      O.NotNull)
    def updatedAt = column[LocalDateTime]("updatedat", O.DBType("timestamp"),
      O.NotNull)
    def isPublic = column[Boolean]("ispublic", O.DBType("boolean"), O.NotNull)

    def * = (schemaId, vendor, name, format, version, schema, createdAt,
      updatedAt, isPublic) <> (Schema.tupled, Schema.unapply)
  }

  //Object used to access the table
  val schemas = TableQuery[Schemas]

  //Case classes for json formatting
  case class MetadataContainer(metadata: Metadata)
  case class Metadata(location: String, createdAt: String, updatedAt: String,
    permissions: Permission)
  case class Permission(read: String, write: String)
  case class ResMetadata(vendor: String, name: String, format: String,
    version: String, metadata: Metadata)

  val selfDescVendor = "com.snowplowanalytics.self-desc"
  val selfDescName = "schema"
  val selfDescFormat = "jsonschema"
  val selfDescVersion = "1-0-0"

  /**
   * Creates the schemas table.
   */
  def createTable() = db withDynSession { schemas.ddl.create }

  /**
   * Deletes the schemas table.
   */
  def dropTable = db withDynSession { schemas.ddl.drop }

  def bootstrapSelfDescSchema(): Unit = if (!bootstrapSchemaExists) {
    val source = Source.fromURL(getClass.getResource("/valid-schema.json"))
    val lines = source.getLines mkString "\n"
    source.close
    add(selfDescVendor, selfDescName, selfDescFormat, selfDescVersion, lines,
      selfDescVendor, "write", true)
  }

  /**
   * Whether the self-desc schema exists in the database
   */
  private def bootstrapSchemaExists(): Boolean = db withDynSession {
    ! (for {
      s <- schemas if
        s.vendor === "com.snowplowanalytics.snowplow" &&
        s.name === "self-desc" &&
        s.format === "jsonschema"
    } yield s).list.isEmpty
  }

  /**
   * Gets every schema belongig to a specific vendor.
   * @param vendors schemas' vendors
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the list of all schemas
   * of this vendor
   */
  def getFromVendor(vendors: List[String], owner: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if s.vendor inSet vendors
      } yield s).list

      if (preliminaryList.length == 0) {
        (NotFound, result(404, "There are no schemas for this vendor"))
      } else {
        val l: List[JValue] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s =>
              parse(s.schema) merge Extraction.decompose(
                MetadataContainer(
                  Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                    s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                    s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                    getPermission(s.vendor, owner, permission, s.isPublic)))))

        if (l.length == 1) {
          (OK, writePretty(l(0)))
        } else if (l.length > 1) {
          (OK, writePretty(l))
        } else {
          (Unauthorized, result(401, "You do not have sufficient privileges"))
        }
      }
    }

  /**
   * Gets metadata about every schemas belonging to a specific vendor.
   * @param vendors schemas' vendors
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing metadata about every schema
   * of this vendor
   */
  def getMetadataFromVendor(vendors: List[String], owner: String,
    permission: String): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if s.vendor inSet vendors
        } yield s).list

        if(preliminaryList.length == 0) {
          (NotFound, result(404, "There are no schemas for this vendor"))
        } else {
          val l: List[ResMetadata] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
                Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                  s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                  s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                  getPermission(s.vendor, owner, permission, s.isPublic))))

          if (l.length == 1) {
            (OK, writePretty(l(0)))
          } else if (l.length > 1) {
            (OK, writePretty(l))
          } else {
            (Unauthorized, result(401, "You do not have sufficient privileges"))
          }
        }
      }

  /**
   * Gets every schemas for this vendor, names combination.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the list of all schemas
   * satifsfying the query
   */
  def getFromName(vendors: List[String], names: List[String], owner: String,
    permission: String): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor inSet vendors) &&
            (s.name inSet names)
        } yield s).list

        if (preliminaryList.length == 0) {
          (NotFound, result(404,
            "There are no schemas for this vendor, name combination"))
        } else {
          val l: List[JValue] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s =>
                parse(s.schema) merge Extraction.decompose(
                  MetadataContainer(
                    Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                      s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                      s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                      getPermission(s.vendor, owner, permission, s.isPublic)))))

          if (l.length == 1) {
            (OK, writePretty(l(0)))
          } else if (l.length > 1) {
            (OK, writePretty(l))
          } else {
            (Unauthorized, result(401, "You do not have sufficient privileges"))
          }
        }
      }

  /**
   * Gets metadata about every schemas for this vendor, names combination.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing metadata about the schemas
   * satifsfying the query
   */
  def getMetadataFromName(vendors: List[String], names: List[String],
    owner: String, permission: String): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor inSet vendors) &&
            (s.name inSet names)
        } yield s).list

        if (preliminaryList.length == 0) {
          (NotFound, result(404,
            "There are no schemas for this vendor, name combination"))
        } else {
          val l: List[ResMetadata] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
                Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                  s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                  s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                  getPermission(s.vendor, owner, permission, s.isPublic))))

          if (l.length == 1) {
            (OK, writePretty(l(0)))
          } else if (l.length > 1) {
            (OK, writePretty(l))
          } else {
            (Unauthorized, result(401, "You do not have sufficient privileges"))
          }
        }
      }

  /**
   * Retrieves every version of a schema.
   * @param vendors schemas' vendors
   * @param names schenas' names
   * @param schemaFormats schemas' formats
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the list of every version of
   * a schema
   */
  def getFromFormat(vendors: List[String], names: List[String],
    schemaFormats: List[String], owner: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor inSet vendors) &&
          (s.name inSet names) &&
          (s.format inSet schemaFormats)
      } yield s).list

      if (preliminaryList.length == 0) {
        (NotFound, result(404,
          "There are no schemas for this vendor, name, format combination"))
      } else {
        val l: List[JValue] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s =>
              parse(s.schema) merge Extraction.decompose(
                MetadataContainer(
                  Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                    s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                    s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                    getPermission(s.vendor, owner, permission, s.isPublic)))))

        if (l.length == 1) {
          (OK, writePretty(l(0)))
        } else if (l.length > 1) {
          (OK, writePretty(l))
        } else {
          (Unauthorized, result(401, "You do not have sufficient privileges"))
        }
      }
    }

  /**
   * Gets metadata about every version of a schema.
   * @param vendors schemas' vendors
   * @param names schemas' names
   * @param schemaFormats schemas' formats
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing metadata about every version
   * of a schema
   */
  def getMetadataFromFormat(vendors: List[String], names: List[String],
    schemaFormats: List[String], owner: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor inSet vendors) &&
          (s.name inSet names) &&
          (s.format inSet schemaFormats)
      } yield s).list

      if (preliminaryList.length == 0) {
        (NotFound, result(404,
          "There are no schemas for this vendor, name, format combination"))
      } else {
        val l: List[ResMetadata] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                getPermission(s.vendor, owner, permission, s.isPublic))))

        if (l.length == 1) {
          (OK, writePretty(l(0)))
        } else if (l.length > 1) {
          (OK, writePretty(l))
        } else {
          (Unauthorized, result(401, "You do not have sufficient privileges"))
        }
      }
    }

  /**
   * Gets a single schema specifying all its characteristics.
   * @param vendors the schema's vendors
   * @param names the schema's names
   * @param schemaFormats the schema's formats
   * @param versions the schema's versions
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the schema
   */
  def get(vendors: List[String], names: List[String],
    schemaFormats: List[String], versions: List[String], owner: String,
    permission: String): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor inSet vendors) &&
            (s.name inSet names) &&
            (s.format inSet schemaFormats) &&
            (s.version inSet versions)
        } yield s).list

        if (preliminaryList.length == 0) {
          (NotFound, result(404, "There are no schemas available here"))
        } else {
          val l: List[JValue] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s =>
                parse(s.schema) merge Extraction.decompose(
                  MetadataContainer(
                    Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                      s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                      s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                      getPermission(s.vendor, owner, permission, s.isPublic)))))

          if (l.length == 1) {
            (OK, writePretty(l(0)))
          } else if (l.length > 1) {
            (OK, writePretty(l))
          } else {
            (Unauthorized, result(401, "You do not have sufficient privileges"))
          }
        }
      }

  /**
   * Gets only metadata about the schema: its vendor, name, format and version.
   * @param vendors the schema's vendors
   * @param names the schema's names
   * @param schemaFormats the schea's formats
   * @param versions the schema's versions
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the metadata
   */
  def getMetadata(vendors: List[String], names: List[String],
    schemaFormats: List[String], versions: List[String], owner: String,
    permission: String): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor inSet vendors) &&
            (s.name inSet names) &&
            (s.format inSet schemaFormats) &&
            (s.version inSet versions)
        } yield s).list

        if (preliminaryList.length == 0) {
          (NotFound, result(404, "There are no schemas available here"))
        } else {
          val l: List[ResMetadata] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
                Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                  s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                  s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                  getPermission(s.vendor, owner, permission, s.isPublic))))

          if (l.length == 1) {
            (OK, writePretty(l(0)))
          } else if (l.length > 1) {
            (OK, writePretty(l))
          } else {
            (Unauthorized, result(401, "You do not have sufficient privileges"))
          }
        }
      }

  /**
   * Gets every public schema.
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the schemas
   */
  def getPublicSchemas(owner: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val l: List[JValue] = (for {
        s <- schemas if s.isPublic
      } yield s)
        .list
        .map(s =>
          parse(s.schema) merge Extraction.decompose(
            MetadataContainer(
              Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
                s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
                s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
                getPermission(s.vendor, owner, permission, s.isPublic)))))

      if (l.length == 1) {
        (OK, writePretty(l(0)))
      } else if (l.length > 1) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no public schemas available"))
      }
    }

  /**
   * Gets metadata about every public schema.
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @return a status code and json pair containing the metadata
   */
  def getPublicMetadata(owner: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val l: List[ResMetadata] = (for {
        s <- schemas if s.isPublic
      } yield s)
        .list
        .map(s => ResMetadata(s.vendor, s.name, s.format, s.version,
          Metadata(buildLoc(s.vendor, s.name, s.format, s.version),
            s.createdAt.toString("MM/dd/yyyy HH:mm:ss"),
            s.updatedAt.toString("MM/dd/yyyy HH:mm:ss"),
            getPermission(s.vendor, owner, permission, s.isPublic))))

      if (l.length == 1) {
        (OK, writePretty(l(0)))
      } else if (l.length > 1) {
        (OK, writePretty(l))
      } else {
        (NotFound, result(404, "There are no public schemas avilable"))
      }
    }

  /**
   * Adds a schema after validating it does not already exist.
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @param schema the schema itself
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @param isPublic whether or not the schema is publicly available
   * @return a status code json response pair
   */
  def add(vendor: String, name: String, format: String, version: String,
    schema: String, owner: String, permission: String,
    isPublic: Boolean = false): (StatusCode, String) =
      if ((permission == "write" || permission == "super") && (vendor.startsWith(owner) || owner == "*")) {
        db withDynSession {
          get(List(vendor), List(name), List(format), List(version), owner,
            permission) match {
              case (OK, j) => (Unauthorized,
                result(401, "This schema already exists"))
              case _ => {
                val now = new LocalDateTime()
                schemas.insert(
                  Schema(0, vendor, name, format, version, schema,
                  now, now, isPublic))
                } match {
                    case 0 => (InternalServerError,
                      result(500, "Something went wrong"))
                    case n => (Created, result(201, "Schema successfully added",
                      buildLoc(vendor, name, format, version)))
                  }
            }
        }
      } else {
        (Unauthorized, result(401, "You do not have sufficient privileges"))
      }

  /**
   * Updates or creates a schema after validating it does not already exist.
   * @param vendor the schema's vendor
   * @param name the schema's name
   * @param format the schema's format
   * @param version the schema's version
   * @param schema the schema itself
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @param isPublic whether or not the schema is publicly available
   * @return a status code json response pair
   */
   def update(vendor: String, name: String, format: String, version: String,
     schema: String, owner: String, permission: String,
     isPublic: Boolean = false): (StatusCode, String) = {
       if (permission == "write" &&( (vendor startsWith owner) || owner == "*")) {
         db withDynSession {
           get(List(vendor), List(name), List(format), List(version), owner,
             permission) match {
               case (OK, j) =>
                 schemas
                   .filter(s => s.vendor === vendor &&
                     s.name === name &&
                     s.format === format &&
                     s.version === version)
                   .map(s => (s.schema, s.isPublic, s.updatedAt))
                   .update(schema, isPublic, new LocalDateTime()) match {
                     case 1 => (OK, result(200, "Schema successfully updated",
                       buildLoc(vendor, name, format, version)))
                     case _ => (InternalServerError,
                       result(500, "Something went wrong"))
                   }
               case (NotFound, j) => {
                val now = new LocalDateTime()
                 schemas
                   .insert(
                     Schema(0, vendor, name, format, version, schema,
                       now, now,
                       isPublic))
                   } match {
                         case 0 => (InternalServerError,
                           result(500, "Something went wrong"))
                         case n => (Created,
                           result(201, "Schema successfully added",
                             buildLoc(vendor, name, format, version)))
                       }
               case we => we
             }
         }
       } else {
         (Unauthorized, result(401, "You do not have sufficient privileges"))
       }
     }

  def delete(vendor: String, name: String, format: String, version: String,
    owner: String, permission: String,
    isPublic: Boolean = false): (StatusCode, String)  =
      if (permission == "write" &&( (vendor startsWith owner) || owner == "*")) {
        db withDynSession {
          schemas.filter(s =>
            s.vendor === vendor &&
            s.name === name &&
            s.format === format &&
            s.version === version)
          .delete match {
            case 0 => (404, "Schema not found")
            case 1 => (OK, "Schema successfully deleted")
            case n => (OK, s"$n schemas successfully deleted")
          }
        }
      } else {
        (Unauthorized, result(401, "You do not have sufficient privileges"))
      }

   /**
    * Validates the the instance provided is valid against the specified schema.
    * @param vendor the schema's vendor
    * @param name the schema's name
    * @param format the schema's format
    * @param version the schema's version
    * @param instance the instance to be validated
    * @return a status code and validation message pair
    */
   def validate(vendor: String, name: String, format: String, version: String,
     instance: String): (StatusCode, String) =
       getNoMetadata(vendor, name, format, version) match {
         case "not found" => (NotFound,
           result(404, "The schema to validate against was not found"))
         case schema => parseOpt(instance) match {
           case Some(jvalue) => {
             val jsonNode = asJsonNode(jvalue)
             val schemaNode = asJsonNode(parse(schema))

             validateAgainstSchema(jsonNode, schemaNode) match {
               case scalaz.Success(j) =>
                 (OK, result(200,
                   "The instance provided is valid against the schema"))
               case Failure(l) => (BadRequest, result(400,
                 "The instance provided is not valid against the schema",
                 fromJsonNode(l.head.asJson)))
             }
           }
           case None =>
             (BadRequest, result(400, "The instance provided is not valid"))
         }
       }

  /**
   * Validates that the schema provided is self-describing.
   * @param schema the schema to be validated
   * @param format the schema format to validate against
   * @param provideSchema if we return the schema or not
   * @return a status code and schema/validation message pair
   */
  def validateSchema(schema: String, format: String,
    provideSchema: Boolean = true): (StatusCode, String) =
      format match {
        case "jsonschema" => {
          parseOpt(schema) match {
            case Some(jvalue) => {
              val jsonNode = asJsonNode(jvalue)
              val schemaNode =
                asJsonNode(parse(getNoMetadata(selfDescVendor, selfDescName,
                  selfDescFormat, selfDescVersion)))

              validateAgainstSchema(jsonNode, schemaNode) match {
                case scalaz.Success(j) =>
                  if (provideSchema) {
                    (OK, schema)
                  } else {
                    (OK, result(200,
                      "The schema provided is a valid self-describing schema"))
                  }
                case Failure(l) => (BadRequest,
                  result(400,
                    "The schema provided is not a valid self-describing schema",
                    fromJsonNode(l.head.asJson)))
              }
            }
            case None =>
              (BadRequest, result(400, "The schema provided is not valid"))
          }
        }
        case _ => (BadRequest,
          result(400, "The schema format provided is not supported"))
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

        if (l.length == 1) {
          l(0)
        } else {
          "not found"
        }
      }

  /**
   * Helper method to build the location of the schema from its metadata.
   * @param vendor schema's vendor
   * @param name schema's name
   * @param format schema's format
   * @param version schema's version
   * @return the location of the schema
   */
  private def buildLoc(vendor: String, name: String, format: String,
    version: String): String =
      List("", "api", "schemas", vendor, name, format, version) mkString("/")

  /**
   * Helper method to construct an appropriate permission object.
   * @param vendor schema's vendor
   * @param owner API key's owner
   * @param permission API key's permission
   * @param isPublic whether or not the schema is public
   * @return an appropriate Permission object
   */
  private def getPermission(vendor: String, owner: String, permission: String,
    isPublic: Boolean): Permission =
      Permission(
        if (isPublic) {
          "public"
        } else {
          "private"
        },
        if( ((vendor startsWith owner) || owner == "*") && permission == "write") {
          "private"
        } else {
          "none"
        })
}
