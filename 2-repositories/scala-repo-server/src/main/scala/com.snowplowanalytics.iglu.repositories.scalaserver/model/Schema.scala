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

// Slick
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._

//Spray
import spray.json.DefaultJsonProtocol._

case class Schema(
  schemaId: Int,
  vendor: String,
  name: String,
  format: String,
  version: String,
  schema: String
  //created: DateTime
)

object SchemaDAO {
  class SchemTable(tag: Tag) extends Table[Schema](tag, "schemas") {
    def schemaId = column[Int](
      "schemaId", O.AutoInc, O.PrimaryKey, O.DBType("bigint"))
    def vendor = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def name = column[String]("name", O.DBType("varchar(50)"), O.NotNull)
    def format = column[String]("format", O.DBType("varchar(50)"), O.NotNull)
    def version = column[String]("version", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[String]("version", O.DBType("json"), O.NotNull)
    //def created = column[DateTime]("created", O.DBType("timestamp"), O.NotNull)

    def * = (schemaId ~ vendor ~ name ~ format ~ version ~ schema) <>
      (Schema, Schema.unapply _)

    def forInsert = 
      (vendor ~ name ~ format ~ version ~ schema) returning schemaId
  }

  case class Result(result: String)
  implicit val resultFormat = jsonFormat1(Result)
  def result(res: String) = new Result(res).toJson.compactPrint

  def getAllFromVendor(vendor: String): Option[String] = {
    val l = Query(SchemTable).where(_.vendor is vendor).list
    if (l.count > 0) {
      Some(l.toJson.compactPrint)
    } else {
      None
    }
  }

  def getAllFromName(vendor: String, name: String): Option[String] = {
    val l = Query(SchemaTable).where(s => s.vendor is vendor && s.name is name).
      list
    if (l.count > 0) {
      Some(l.toJson.compactPrint)
    } else {
      None
    }
  }

  def getAllFromFormat(vendor: String, name: String, format: String):
  Option[String] = {
    val l = Query(SchemaTable).where(s =>
        s.vendor is vendor &&
        s.name is name &&
        s.format is format).list
    if (l.count > 0) {
      Some(l.toJson.compactPrint)
    } else {
      None
    }
  }

  def get(vendor: String, name: String, format: String, version: String):
    Option[String] = {
      val l = Query(SchemaTable).where(s =>
          s.vendor is vendor &&
          s.name is name &&
          s.format is format &&
          s.version is version).list
      if (l.count == 1) {
        Some(l.toJson.compactPrint)
      } else {
        None
      }
    }

  def add(vendor: String, name: String, format: String, version: String,
    schema: String): String =
      SchemaTable.forInsert.
        insert(vendor, name, format, version, schema) match {
          case 0 => result("Something went wrong")
          case n => result("Schema successfully added")
        }
}
