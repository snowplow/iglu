/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
import validation.ValidatableJsonMethods.validateAgainstSchema

// Joda
import org.joda.time.LocalDateTime

// Json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.Extraction
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.writePretty

// Scala
import scala.annotation.meta.field
import scala.io.Source

// cats
import cats.data.{ Validated, NonEmptyList, ValidatedNel }
import cats.instances.list._
import cats.instances.tuple._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.syntax.apply._
import cats.syntax.validated._

// Slick
import Database.dynamicSession

// Akka Http
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._

// Swagger
import io.swagger.annotations.{ApiModel, ApiModelProperty}

// Iglu
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Schema => SchemaAst }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.implicits._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{SelfSyntaxChecker, JsonPointer}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.{ allLintersMap, Message, Level }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SanityLinter.lint
import com.snowplowanalytics.iglu.core.SelfDescribingSchema
import com.snowplowanalytics.iglu.core.json4s.implicits._

import SchemaDAO._

/**
  * Case class representing a schema in the database.
  * @constructor create a schema from required data
  * @param schemaId primary key
  * @param vendor the schema's vendor
  * @param name the schema's name
  * @param format the schema's format
  * @param version the schema's version
  * @param schema the schema
  * @param createdAt data at which point the schema was created
  */
@ApiModel(value = "Schema", description = "represents a schema in the database")
case class Schema(
                   @(ApiModelProperty @field)(value = "ID of schema", hidden = true)
                   schemaId: Int,
                   @(ApiModelProperty @field)(value = "Vendor of schema")
                   vendor: String,
                   @(ApiModelProperty @field)(value = "Name of schema")
                   name: String,
                   @(ApiModelProperty @field)(value = "Format of schema")
                   format: String,
                   @(ApiModelProperty @field)(value = "Version of schema")
                   version: String,
                   @(ApiModelProperty @field)(value = "Draft number of schema")
                   draftNumber: String,
                   @(ApiModelProperty @field)(value = "Schema")
                   schema: String,
                   @(ApiModelProperty @field)(value = "Date at which schema was created", hidden = true)
                   createdAt: LocalDateTime,
                   @(ApiModelProperty @field)(value = "Date at which schema was updated", hidden = true)
                   updatedAt: LocalDateTime,
                   @(ApiModelProperty @field)(value = "Publicity of schema", hidden = true)
                   isPublic: Boolean
                 )

/**
  * DAO for accessing the schemas table in the database
  * @constructor create a schema DAO with a reference to the database
  * @param db a reference to a ``Database``
  */
class SchemaDAO(val db: Database) extends DAO {


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
    def draftNumber = column[String]("draftnumber", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[String]("schema", O.DBType("text"), O.NotNull)
    def createdAt = column[LocalDateTime]("createdat", O.DBType("timestamp"), O.NotNull)
    def updatedAt = column[LocalDateTime]("updatedat", O.DBType("timestamp"), O.NotNull)
    def isPublic = column[Boolean]("ispublic", O.DBType("boolean"), O.NotNull)

    def * = (schemaId, vendor, name, format, version, draftNumber, schema, createdAt,
      updatedAt, isPublic) <> (Schema.tupled, Schema.unapply)
  }

  // Object used to access the table
  val schemas = TableQuery[Schemas]

  sealed trait MetadataResult extends Product with Serializable
  object MetadataResult {
    def fromSchema(schema: Schema, owner: String, permission: String, isDraft: Boolean): MetadataResult = {
      val metadata = Metadata.fromSchema(schema, owner, permission, isDraft)
      if (isDraft)
        DraftMetadata(schema.vendor, schema.name, schema.format, schema.draftNumber, metadata)
      else
        ResMetadata(schema.vendor, schema.name, schema.format, schema.version, metadata)
    }
  }

  // Case classes for json formatting
  case class MetadataContainer(metadata: Metadata)
  object MetadataContainer {
    def fromSchema(schema: Schema, owner: String, permission: String, isDraft: Boolean): MetadataContainer = {
      val metadata = Metadata.fromSchema(schema, owner, permission, isDraft)
      MetadataContainer(metadata)
    }

    def asJson(schema: Schema, owner: String, permission: String, isDraft: Boolean): JValue = {
      val metadataJson = Extraction.decompose(fromSchema(schema, owner, permission, isDraft))
      parse(schema.schema) merge metadataJson
    }
  }

  case class Metadata(location: String, createdAt: String, updatedAt: String, permissions: Permission)
  object Metadata {
    def fromSchema(schema: Schema, owner: String, permission: String, isDraft: Boolean): Metadata = {
      val location = if (isDraft)
        buildDraftLoc(schema.vendor, schema.name, schema.format, schema.draftNumber)
      else buildLoc(schema.vendor, schema.name, schema.format, schema.version)
      val perm = getPermission(schema.vendor, owner, permission, schema.isPublic)
      Metadata(location, formatDate(schema.createdAt), formatDate(schema.updatedAt), perm)
    }
  }

  def formatDate(dateTime: LocalDateTime): String =
    dateTime.toString("MM/dd/yyyy HH:mm:ss")

  def toIgluUri(schema: Schema): String =
    s"iglu:${schema.vendor}/${schema.name}/${schema.format}/${schema.version}"

  case class Permission(read: String, write: String)
  case class ResMetadata(vendor: String, name: String, format: String,
                         version: String, metadata: Metadata) extends MetadataResult
  case class DraftMetadata(vendor: String, name: String, format: String,
                                 draftNumber: String, metadata: Metadata) extends MetadataResult

  val selfDescVendor = "com.snowplowanalytics.self-desc"
  val selfDescName = "schema"
  val selfDescFormat = "jsonschema"
  val selfDescVersion = "1-0-0"

  // draftNumber of schemas which are not draft
  val notDraftNumber = "0"

  // Slick check to be used in all schema queries
  def draftNumberCheck(draftNumber: Column[String], isDraft: Boolean): Column[Boolean] =
    if (isDraft) draftNumber =!= notDraftNumber else draftNumber === notDraftNumber

  def permissionCheck(owner: String, s: Schemas): Column[Boolean] = owner match {
    case "-" => s.isPublic
    case "*" => true
    case o => s.vendor.startsWith(o) || s.isPublic
  }

  /**
    * Creates the schemas table.
    */
  def createTable() = db withDynSession { schemas.ddl.create }

  /**
    * Deletes the schemas table.
    */
  def dropTable = db withDynSession { schemas.ddl.drop }

  def bootstrapSelfDescSchema(): Unit =
    if (!bootstrapSchemaExists) {
      val source = Source.fromURL(getClass.getResource("/valid-schema.json"))
      val lines = source.getLines mkString "\n"
      source.close
      add(selfDescVendor, selfDescName, selfDescFormat, selfDescVersion, notDraftNumber, lines,
        selfDescVendor, "write", isPublic = true, isDraft = false)
    }

  /**
    * Whether the self-desc schema exists in the database
    */
  private def bootstrapSchemaExists(): Boolean =
    db withDynSession {
      (for {
        s <- schemas if
        s.vendor === "com.snowplowanalytics.snowplow" &&
          s.name === "self-desc" &&
          s.format === "jsonschema"
      } yield s).list.nonEmpty
    }

  /**
    * Gets every schema belonging to a specific vendor.
    * @param vendor schemas' vendor
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the list of all schemas
    * of this vendor
    */
  def getFromVendor(vendor: String, owner: String,
                    permission: String, includeMetadata: Boolean, isDraft: Boolean): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = schemas.filter(s => (s.vendor === vendor) && draftNumberCheck(s.draftNumber, isDraft)).list

        if (preliminaryList.isEmpty) {
          (NotFound, result(404, "There are no schemas available here"))
        } else {
          val jsonSchemas: List[JValue] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => if (includeMetadata) MetadataContainer.asJson(s, owner, permission, isDraft) else parse(s.schema))

          jsonSchemas match {
            case Nil => (NotFound, result(404, "There are no schemas available here"))
            case single :: Nil => (OK, writePretty(single))
            case multiple => (OK, writePretty(multiple))
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
  def getMetadataFromVendor(vendors: String, owner: String,
                            permission: String, isDraft: Boolean): (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor === vendors) && draftNumberCheck(s.draftNumber, isDraft)
      } yield s).list

      if(preliminaryList.isEmpty) {
        (NotFound, result(404, "There are no schemas available here"))
      } else {
        val metadata: List[MetadataResult] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s => MetadataResult.fromSchema(s, owner, permission, isDraft))

        metadata match {
          case Nil => (NotFound, result(404, "There are no schemas available here"))
          case single :: Nil => (OK, writePretty(single))
          case multiple => (OK, writePretty(multiple))
        }
      }
    }

  /**
    * Gets every schemas for this vendor, names combination.
    * @param vendor schemas' vendors
    * @param name schemas' names
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the list of all schemas
    * satifsfying the query
    */
  def getFromName(vendor: String, name: String, owner: String,
                  permission: String, includeMetadata: Boolean, isDraft: Boolean): (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor === vendor) &&
          (s.name === name) && draftNumberCheck(s.draftNumber, isDraft)
      } yield s).list

      if (preliminaryList.isEmpty) {
        (NotFound, result(404, "There are no schemas available here"))
      } else {
        val jsonSchemas: List[JValue] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s => if (includeMetadata) MetadataContainer.asJson(s, owner, permission, isDraft) else parse(s.schema))

        jsonSchemas match {
          case Nil => (NotFound, result(404, "There are no schemas available here"))
          case single :: Nil => (OK, writePretty(single))
          case multiple => (OK, writePretty(multiple))
        }
      }
    }

  /**
    * Gets metadata about every schemas for this vendor, names combination.
    * @param vendor schemas' vendors
    * @param name schemas' names
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing metadata about the schemas
    * satifsfying the query
    */
  def getMetadataFromName(vendor: String, name: String,
                          owner: String, permission: String, isDraft: Boolean): (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor === vendor) &&
          (s.name === name) && draftNumberCheck(s.draftNumber, isDraft)
      } yield s).list

      if (preliminaryList.isEmpty) {
        (NotFound, result(404,
          "There are no schemas available here"))
      } else {
        val metadata: List[MetadataResult] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s => MetadataResult.fromSchema(s, owner, permission, isDraft))

        metadata match {
          case Nil => (NotFound, result(404, "There are no schemas available here"))
          case single :: Nil => (OK, writePretty(single))
          case multiple => (OK, writePretty(multiple))
        }
      }
    }

  /**
    * Retrieves every version of a schema.
    * @param vendor schemas' vendors
    * @param name schenas' names
    * @param format schemas' formats
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the list of every version of
    * a schema
    */
  def getFromFormat(vendor: String, name: String,
                    format: String, owner: String,
                    permission: String, includeMetadata: Boolean, isDraft: Boolean):
    (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor === vendor) &&
            (s.name === name) &&
            (s.format === format) &&
            draftNumberCheck(s.draftNumber, isDraft)
        } yield s).list

        if (preliminaryList.isEmpty) {
          (NotFound, result(404,
            "There are no schemas available here"))
        } else {
          val jsonSchemas: List[JValue] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => if (includeMetadata) MetadataContainer.asJson(s, owner, permission, isDraft) else parse(s.schema))

          jsonSchemas match {
            case Nil => (NotFound, result(404, "There are no schemas available here"))
            case single :: Nil => (OK, writePretty(single))
            case multiple => (OK, writePretty(multiple))
          }
        }
      }

  /**
    * Gets metadata about every version of a schema.
    * @param vendor schemas' vendors
    * @param name schemas' names
    * @param format schemas' formats
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing metadata about every version
    * of a schema
    */
  def getMetadataFromFormat(vendor: String, name: String, format: String, owner: String,
                            permission: String, isDraft: Boolean): (StatusCode, String) =
      db withDynSession {
        val preliminaryList = (for {
          s <- schemas if (s.vendor === vendor) &&
            (s.name === name) &&
            (s.format === format) &&
            draftNumberCheck(s.draftNumber, isDraft)
        } yield s).list

        if (preliminaryList.isEmpty) {
          (NotFound, result(404,
            "There are no schemas available here"))
        } else {
          val metadata: List[MetadataResult] =
            preliminaryList
              .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
              .map(s => MetadataResult.fromSchema(s, owner, permission, isDraft))

          metadata match {
            case Nil => (NotFound, result(404, "There are no schemas available here"))
            case single :: Nil => (OK, writePretty(single))
            case multiple => (OK, writePretty(multiple))
          }
        }
      }

  /**
    * Gets a single schema specifying all its characteristics.
    * @param vendors the schema's vendors
    * @param names the schema's names
    * @param schemaFormats the schema's formats
    * @param version the schema's version
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the schema
    */
  def get(vendors: String, names: String, schemaFormats: String, version: String,
          draftNumbers: String, owner: String, permission: String, includeMetadata: Boolean, isDraft: Boolean):
  (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas
        if (s.vendor === vendors) &&
          (s.name === names) &&
          (s.format === schemaFormats) &&
          (if (isDraft) draftNumberCheck(s.draftNumber, isDraft) else s.version === version)
      } yield s).list

      if (preliminaryList.isEmpty) {
        (NotFound, result(404, "There are no schemas available here"))
      } else {
        val jsonSchemas: List[JValue] =
          preliminaryList
            .filter(s => (s.vendor.startsWith(owner) || owner == "*") || s.isPublic)
            .map(s => if (includeMetadata) MetadataContainer.asJson(s, owner, permission, isDraft) else parse(s.schema))

        jsonSchemas match {
          case Nil => (NotFound, result(404, "There are no schemas available here"))
          case single :: Nil => (OK, writePretty(single))
          case multiple => (OK, writePretty(multiple))
        }
      }
    }

  /**
    * Gets only metadata about the schema: its vendor, name, format and version.
    * @param vendor the schema's vendors
    * @param name the schema's names
    * @param format the schea's formats
    * @param version the schema's versions
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the metadata
    */
  def getMetadata(vendor: String, name: String,
                  format: String, version: String, draftNumbers: String, owner: String,
                  permission: String, isDraft: Boolean): (StatusCode, String) =
    db withDynSession {
      val preliminaryList = (for {
        s <- schemas if (s.vendor === vendor) &&
          (s.name === name) &&
          (s.format === format) &&
          (if (isDraft) s.draftNumber === draftNumbers else s.version === version) &&
          draftNumberCheck(s.draftNumber, isDraft)
      } yield s).list

      if (preliminaryList.isEmpty) {
        (NotFound, result(404, "There are no schemas available here"))
      } else {
        val metadata: List[MetadataResult] =
          preliminaryList
            .filter(s => ((s.vendor startsWith owner) || owner == "*") || s.isPublic)
            .map(s => MetadataResult.fromSchema(s, owner, permission, isDraft))

        metadata match {
          case Nil => (NotFound, result(404, "There are no schemas available here"))
          case single :: Nil => (OK, writePretty(single))
          case multiple => (OK, writePretty(multiple))
        }
      }
    }

  /**
    * Gets every public schema
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @return a status code and json pair containing the schemas
    */
  def getAllSchemas(owner: String, permission: String, includeMetadata: Boolean, isDraft: Boolean, body: Boolean): (StatusCode, String) = {
    def prepare(schema: Schema) =
      if (includeMetadata) MetadataContainer.asJson(schema, owner, permission, isDraft)
      else if (!body) JString(toIgluUri(schema))
      else parse(schema.schema)

    db withDynSession {
      schemas
        .filter { s => permissionCheck(owner, s) && draftNumberCheck(s.draftNumber, isDraft) }
        .list
        .map(prepare) match {
        case Nil => (NotFound, result(404, "There are no schemas available here"))
        case multiple => (OK, writePretty(multiple))
      }
    }
  }

  def getAllMetadata(owner: String, permission: String, isDraft: Boolean): (StatusCode, String) =
    db withDynSession {
      schemas
        .filter(s => permissionCheck(owner, s) && draftNumberCheck(s.draftNumber, isDraft))
        .list
        .map(s => MetadataResult.fromSchema(s, owner, permission, isDraft)) match {
        case Nil => (NotFound, result(404, "There are no schemas available here"))
        case multiple => (OK, writePretty(multiple))
      }
    }

  /**
    * Adds a schema after validating it does not already exist.
    * @param vendor the schema's vendor
    * @param name the schema's name
    * @param format the schema's format
    * @param version the schema's version
    * @param draftNumber the schema's draft number
    * @param schema the schema itself
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    * @param isPublic whether or not the schema is publicly available
    * @return a status code json response pair
    */
  def add(vendor: String, name: String, format: String, version: String, draftNumber: String,
          schema: String, owner: String, permission: String,
          isPublic: Boolean = false, isDraft: Boolean): (StatusCode, String) =
    if ((permission == "write" || permission == "super") && (vendor.startsWith(owner) || owner == "*")) {
      db withDynSession {
        get(vendor, name, format, version, draftNumber, owner, permission, includeMetadata = false, isDraft) match {
          case (OK, j) => (Unauthorized, result(401, "This schema already exists in the registry"))
          case _ => {
            val now = new LocalDateTime()
            schemas.insert(Schema(0, vendor, name, format, version=version, draftNumber=draftNumber, schema, now, now, isPublic))
          } match {
            case 0 => (InternalServerError, result(500, "Something went wrong, we could not create the schema"))
            case _ => (Created, result(201, "The schema has been successfully added",
                if (isDraft) buildDraftLoc(vendor, name, format, draftNumber)
                else buildLoc(vendor, name, format, version)))
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
  def update(vendor: String, name: String, format: String, version: String, draftNumber: String,
             schema: String, owner: String, permission: String,
             isPublic: Boolean = false, isDraft: Boolean): (StatusCode, String) = {
    if ((permission == "write" || permission == "super") && (vendor.startsWith(owner) || owner == "*")) {
      db withDynSession {
        get(vendor, name, format, version, draftNumber, owner, permission, includeMetadata = false, isDraft) match {
          case (OK, _) =>
            schemas
              .filter(s => s.vendor === vendor &&
                s.name === name &&
                s.format === format &&
                (if (isDraft) s.draftNumber === draftNumber else s.version === version))
              .map(s => (s.schema, s.isPublic, s.updatedAt))
              .update(schema, isPublic, new LocalDateTime()) match {
                case 1 => (OK, result(200, "The schema has been successfully updated",
                  if (isDraft) buildDraftLoc(vendor, name, format, draftNumber)
                  else buildLoc(vendor, name, format, version)))
                case _ => (InternalServerError,
                  result(500, "Something went wrong, we could not update the schema"))
            }
          case (NotFound, _) => {
            val now = new LocalDateTime()
            schemas.insert(Schema(0, vendor, name, format, version, draftNumber, schema, now, now, isPublic))
          } match {
              case 0 => (InternalServerError, result(500, "Something went wrong, we could not update the schema"))
              case _ => (Created, result(201, "The schema has been successfully added",
                if (isDraft) buildDraftLoc(vendor, name, format, draftNumber)
                else buildLoc(vendor, name, format, version)))
            }
          case rest => rest
        }
      }
    } else {
      (Unauthorized, result(401, "You do not have sufficient privileges"))
    }
  }

  def delete(vendor: String, name: String, format: String, version: String, draftNumber: String,
             owner: String, permission: String, isPublic: Boolean = false, isDraft: Boolean): (StatusCode, String) =
    if (permission == "write" &&( (vendor startsWith owner) || owner == "*")) {
      db withDynSession {
        schemas.filter(s =>
          s.vendor === vendor &&
            s.name === name &&
            s.format === format &&
            (if (isDraft) s.draftNumber === draftNumber else s.version === version))
          .delete match {
          case 0 => (404, result(404, "Schema not found"))
          case 1 => (OK, result(200, "The schema has been successfully deleted"))
          case n => (OK, result(200, s"$n schemas successfully deleted"))
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
      case None => (NotFound, result(404, "The schema to validate against was not found"))
      case Some(schema) => parseOpt(instance) match {
        case Some(jvalue) =>
          validateAgainstSchema(jvalue, parse(schema)) match {
            case Validated.Valid(_) =>
              (OK, result(200, "The instance provided is valid against the schema"))
            case Validated.Invalid(l) => (BadRequest, result(400,
              "The instance provided is not valid against the schema",
              fromJsonNode(l.head.asJson)))
          }
        case None =>
          (BadRequest, result(400, "The instance provided is not valid"))
      }
    }

  /**
    * Validates that the schema provided is self-describing.
    * @param schema the schema to be validated
    * @param format the schema format to validate against
    * @return a status code and schema/validation message pair
    */
  def lintSchema(schema: String, format: String): (StatusCode, String) =
    format match {
      case "jsonschema" =>
        validateJsonSchema(schema) match {
          case Right((json, schemaReport)) =>
            val lintReport = SchemaAst.parse(json)
              .fold(NotSchema.invalidNel[SchemaAst])(_.validNel[Message])
              .andThen { ast =>
                val result = lint(ast, allLintersMap.values.toList)
                  .toList
                  .flatMap { case (pointer, issues) => issues.toList.map(_.toMessage(pointer)) }
                NonEmptyList.fromList(result).fold(().validNel[Message])(_.invalid[Unit])
              }
            (schemaReport, lintReport).mapN { (_, _) => () } match {
              case Validated.Valid(_) =>
                (OK, result(200, "The schema provided is a valid self-describing schema"))
              case Validated.Invalid(report) =>
                (OK, result(200, "The schema has some issues", reportToJson(report)))
            }
          case Left(error) =>
            (BadRequest, result(400, error))
        }
      case _ => (BadRequest, result(400, "The schema format provided is not supported"))
    }

  /**
    * Gets a single schema without the metadata associated
    * @param vendor the schema's vendor
    * @param name the schema's name
    * @param format the schema's format
    * @param version the schema's version
    * @return the schema without metadata
    */
  private def getNoMetadata(vendor: String, name: String, format: String, version: String): Option[String] =
    db withDynSession {
      schemas.filter(s =>
        s.vendor === vendor &&
          s.name === name &&
          s.format === format &&
          s.version === version).
        map(_.schema).firstOption
    }

  /**
    * Helper method to build the location of the schema from its metadata.
    * @param vendor schema's vendor
    * @param name schema's name
    * @param format schema's format
    * @param version schema's version
    * @return the location of the schema
    */
  private def buildLoc(vendor: String, name: String, format: String, version: String): String =
    List("", "api", "schemas", vendor, name, format, version).mkString("/")

  /**
    * Helper method to build the location of the draft schema from its metadata.
    * @param vendor schema's vendor
    * @param name schema's name
    * @param format schema's format
    * @param draftNumber schema's draft number
    * @return
    */
  private def buildDraftLoc(vendor: String, name: String, format: String, draftNumber: String): String =
    List("", "api", "draft", vendor, name, format, draftNumber).mkString("/")

  /**
    * Helper method to construct an appropriate permission object.
    * @param vendor schema's vendor
    * @param owner API key's owner
    * @param permission API key's permission
    * @param isPublic whether or not the schema is public
    * @return an appropriate Permission object
    */
  private def getPermission(vendor: String, owner: String, permission: String, isPublic: Boolean): Permission =
    Permission(
      if (isPublic) "public" else "private",
      if ( ((vendor startsWith owner) || owner == "*") && permission == "write") "private" else "none"
    )

}

object SchemaDAO {

  type LintReport[A] = ValidatedNel[Message, A]

  val NotSelfDescribing = Message(JsonPointer.Root, "JSON Schema is not self-describing", Level.Error)
  val NotSchema = Message(JsonPointer.Root, "Cannot extract JSON Schema", Level.Error)

  def validateJsonSchema(schema: String): Either[String, (JValue, LintReport[SelfDescribingSchema[JValue]])] = {
    parseOpt(schema) match {
      case Some(json) =>
        val generalCheck =
          SelfSyntaxChecker.validateSchema(json, false)

        val selfDescribingCheck = SelfDescribingSchema
          .parse(json)
          .fold(_ => NotSelfDescribing.invalidNel[SelfDescribingSchema[JValue]], _.validNel[Message])

        val result = (generalCheck, selfDescribingCheck).mapN { (_: Unit, schema: SelfDescribingSchema[JValue]) => schema }
        (json, result).asRight[String]
      case None =>
        "The schema provided is not valid JSON".asLeft[(JValue, LintReport[SelfDescribingSchema[JValue]])]
    }
  }

  def reportToJson(report: NonEmptyList[Message]): JValue =
    JArray(report.toList.map { message =>
      ("message" -> message.message) ~ ("level" -> message.level.toString.toUpperCase) ~ ("pointer" -> message.jsonPointer.show)
    })
}
