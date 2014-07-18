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
import util.PostgresDB
import util.IgluPostgresDriver.simple._

// Joda
import org.joda.time.LocalDateTime

//Spray
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.json._
import DefaultJsonProtocol._

object SchemaDAO extends PostgresDB with DAO {

  case class Schema(schemaId: Int, vendor: String, name: String, format: String,
    version: String, schema: JsValue, created: LocalDateTime)

  class Schemas(tag: Tag) extends Table[Schema](tag, "schemas") {
    def schemaId = column[Int](
      "schemaid", O.AutoInc, O.PrimaryKey, O.DBType("serial"))
    def vendor = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def name = column[String]("name", O.DBType("varchar(50)"), O.NotNull)
    def format = column[String]("format", O.DBType("varchar(50)"), O.NotNull)
    def version = column[String]("version", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[JsValue]("schema", O.DBType("json"), O.NotNull)
    def created = column[LocalDateTime]("created", O.DBType("timestamp"),
      O.NotNull)

    def * = (schemaId, vendor, name, format, version, schema, created) <>
      (Schema.tupled, Schema.unapply)
  }

  val schemas = TableQuery[Schemas]

  case class ReturnedSchema(schema: JsValue, created: String)
  implicit val schemaFormat = jsonFormat2(ReturnedSchema)

  case class ReturnedSchemaFormat(schema: JsValue, version: String,
    created: String)
  implicit val formatFormat = jsonFormat3(ReturnedSchemaFormat)

  case class ReturnedSchemaName(schema: JsValue, format: String,
    version: String, created: String)
  implicit val nameFormat = jsonFormat4(ReturnedSchemaName)

  case class ReturnedSchemaVendor(schema: JsValue, name: String, format: String,
    version: String, created: String)
  implicit val vendorFormat = jsonFormat5(ReturnedSchemaVendor)

  def createTable = schemas.ddl.create

  def getFromVendor(vendor: String): (StatusCode, String) = {
    val l: List[ReturnedSchemaVendor] =
      schemas.filter(_.vendor === vendor).
        map(s => (s.schema, s.name, s.format, s.version, s.created)).list.
        map(s => ReturnedSchemaVendor(s._1, s._2, s._3, s._4,
          s._5.toString("MM/dd/yyyy HH:mm:ss")))

    if (l.length > 0) {
      (OK, l.toJson.prettyPrint)
    } else {
      (NotFound, result(404, "There are no schemas for this vendor"))
    }
  }

  def getFromName(vendor: String, name: String): (StatusCode, String) = {
    val l: List[ReturnedSchemaName] =
      schemas.filter(s => s.vendor === vendor && s.name === name).
        map(s => (s.schema, s.format, s.version, s.created)).list.
        map(s => ReturnedSchemaName(s._1, s._2, s._3,
          s._4.toString("MM/dd/yyyy HH:mm:ss")))

    if (l.length > 0) {
      (OK, l.toJson.prettyPrint)
    } else {
      (NotFound, result(404,
        "There are no schemas for this vendor, name combination"))
    }
  }

  def getFromFormat(vendor: String, name: String, format: String):
  (StatusCode, String) = {
    val l: List[ReturnedSchemaFormat] =
      schemas.filter(s =>
        s.vendor === vendor &&
        s.name === name &&
        s.format === format).
      map(s => (s.schema, s.version, s.created)).list.
      map(s => ReturnedSchemaFormat(s._1, s._2,
        s._3.toString("MM/dd/yyyy HH:mm:ss")))

    if (l.length > 0) {
      (OK, l.toJson.prettyPrint)
    } else {
      (NotFound, result(404,
        "There are no schemas for this vendor, name, format combination"))
    }
  }

  def get(vendor: String, name: String, format: String, version: String):
    (StatusCode, String) = {
      val l: List[ReturnedSchema] = schemas.filter(s =>
          s.vendor === vendor &&
          s.name === name &&
          s.format === format &&
          s.version === version).
        map(s => (s.schema, s.created)).list.
        map(s => ReturnedSchema(s._1, s._2.toString("MM/dd/yyyy HH:mm:ss")))

      if (l.length == 1) {
        (OK, l(0).toJson.prettyPrint)
      } else {
        (NotFound, result(404, "There are no schemas available here"))
      }
    }

  def add(vendor: String, name: String, format: String, version: String,
    schema: String): (StatusCode, String) =
      get(vendor, name, format, version) match {
        case (OK, j) => (Unauthorized,
          result(401, "This schema already exists"))
        case c => schemas.insert(
          Schema(0, vendor, name, format, version, schema.parseJson,
            new LocalDateTime())) match {
              case 0 => (InternalServerError,
                result(500, "Something went wrong"))
              case n => (OK, result(200, "Schema added successfully"))
            }
      }
}
