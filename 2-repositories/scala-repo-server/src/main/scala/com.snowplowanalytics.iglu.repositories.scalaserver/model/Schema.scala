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

// Slick
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._

//Spray
import spray.json._
import DefaultJsonProtocol._

case class Schema(
  schemaId: Int,
  vendor: String,
  name: String,
  format: String,
  version: String,
  schema: String
  //created: DateTime
)

object SchemaDAO extends PostgresDB {
  class Schemas(tag: Tag) extends Table[Schema](tag, "schemas") {
    def schemaId = column[Int](
      "schemaId", O.AutoInc, O.PrimaryKey, O.DBType("bigint"))
    def vendor = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def name = column[String]("name", O.DBType("varchar(50)"), O.NotNull)
    def format = column[String]("format", O.DBType("varchar(50)"), O.NotNull)
    def version = column[String]("version", O.DBType("varchar(50)"), O.NotNull)
    def schema = column[String]("version", O.DBType("json"), O.NotNull)
    //def created = column[DateTime]("created", O.DBType("timestamp"), O.NotNull)

    def * = (schemaId, vendor, name, format, version, schema) <>
      (Schema.tupled, Schema.unapply)
  }

  val schemas = TableQuery[Schemas]

  case class Result(result: String)
  implicit val resultFormat = jsonFormat1(Result)
  def result(res: String) = new Result(res).toJson.compactPrint

  def getAllFromVendor(vendor: String): Option[String] = {
    val l = schemas.filter(_.vendor === vendor).list
    if (l.length > 0) {
      Some(l.toString)
    } else {
      None
    }
  }

  def getAllFromName(vendor: String, name: String): Option[String] = {
    val l = schemas.filter(s => s.vendor === vendor && s.name === name).list
    if (l.length > 0) {
      Some(l.toString)
    } else {
      None
    }
  }

  def getAllFromFormat(vendor: String, name: String, format: String):
  Option[String] = {
    val l = schemas.filter(s =>
        s.vendor === vendor &&
        s.name === name &&
        s.format === format).list
    if (l.length > 0) {
      Some(l.toString)
    } else {
      None
    }
  }

  def get(vendor: String, name: String, format: String, version: String):
    Option[String] = {
      val l = schemas.filter(s =>
          s.vendor === vendor &&
          s.name === name &&
          s.format === format &&
          s.version === version).list
      if (l.length == 1) {
        Some(l.toString)
      } else {
        None
      }
    }

  def add(vendor: String, name: String, format: String, version: String,
    schema: String): String =
      schemas.map(s => (s.vendor, s.name, s.format, s.version, s.schema)) +=
        (vendor, name, format, version, schema) match {
          case 0 => result("Something went wrong")
          case n => result("Schema successfully added")
        }
}
