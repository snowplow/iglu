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
package com.snowplowanalytics.iglu.repositories.scalaserver
package model

// This project
import util.PostgresDB

// Joda
import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

// Slick
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._

//Spray
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.json._
import DefaultJsonProtocol._

case class Schema(
  schemaId: Int,
  vendor: String,
  name: String,
  format: String,
  version: String,
  schema: String,
  created: DateTime
)

object SchemaDAO extends PostgresDB {
  class Schemas(tag: Tag) extends Table[Schema](tag, "schemas") {
    def schemaId = column[Int](
      "schemaid", O.AutoInc, O.PrimaryKey, O.DBType("serial"))
    def vendor = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def name = column[String]("name", O.DBType("varchar(50)"), O.NotNull)
    def format = column[String]("format", O.DBType("varchar(50)"), O.NotNull)
    def version = column[String]("version", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[String]("schema", O.DBType("varchar(4000)"), O.NotNull)
    def created = column[DateTime]("created", O.DBType("timestamp"), O.NotNull)

    def * = (schemaId, vendor, name, format, version, schema, created) <>
      (Schema.tupled, Schema.unapply)
  }

  val schemas = TableQuery[Schemas]

  def createTable = schemas.ddl.create

  def getFromVendor(vendor: String): (StatusCode, String) = {
    val l: List[(String, String, String, String, String)] =
      schemas.filter(_.vendor === vendor).
        map(s => (s.name, s.format, s.version, s.schema, s.created.toString)).
        list
    if (l.length > 0) {
      (OK, l.toJson.compactPrint)
    } else {
      (NotFound, "There are no schemas for this vendor")
    }
  }

  def getFromName(vendor: String, name: String): (StatusCode, String) = {
    val l: List[(String, String, String, String)] =
      schemas.filter(s => s.vendor === vendor && s.name === name).
        map(s => (s.format, s.version, s.schema, s.created.toString)).list
    if (l.length > 0) {
      (OK, l.toJson.compactPrint)
    } else {
      (NotFound, "There are no schemas for this vendor, name combination")
    }
  }

  def getFromFormat(vendor: String, name: String, format: String):
  (StatusCode, String) = {
    val l: List[(String, String, String)] =
      schemas.filter(s =>
        s.vendor === vendor &&
        s.name === name &&
        s.format === format).
      map(s => (s.version, s.schema, s.created.toString)).list
    if (l.length > 0) {
      (OK, l.toJson.compactPrint)
    } else {
      (NotFound,
        "There are no schemas for this vendor, name, format combination")
    }
  }

  def get(vendor: String, name: String, format: String, version: String):
    (StatusCode, String) = {
      val l: List[(String, String)] = schemas.filter(s =>
          s.vendor === vendor &&
          s.name === name &&
          s.format === format &&
          s.version === version).map(s => (s.schema, s.created.toString)).list
      if (l.length == 1) {
        (OK, l(0).toString)
      } else {
        (NotFound, "There are no schemas available here")
      }
    }

  def add(vendor: String, name: String, format: String, version: String,
    schema: String): (StatusCode, String) =
      get(vendor, name, format, version) match {
        case (OK, j) => (Unauthorized, "This schema already exists")
        case c => schemas.insert(
          Schema(0, vendor, name, format, version, schema, new DateTime()))
            match {
              case 0 => (InternalServerError, "Something went wrong")
              case n => (OK, "Schema added successfully")
            }
      }
}
