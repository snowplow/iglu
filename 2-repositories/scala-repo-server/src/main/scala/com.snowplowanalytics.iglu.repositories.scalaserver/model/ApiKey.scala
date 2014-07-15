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

// Java
import java.util.UUID

// Joda
import org.joda.time.DateTime
import com.github.tototoshi.slick.PostgresJodaSupport._

// Slick
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.lifted.Tag

// Spray
import spray.json._
import DefaultJsonProtocol._
import spray.http.StatusCode
import spray.http.StatusCodes._

case class ApiKey(
  uid: UUID,
  owner: String,
  permission: String,
  created: DateTime
)

object ApiKeyDAO extends PostgresDB {
  class ApiKeys(tag: Tag) extends Table[ApiKey](tag, "apikeys") {
    def uid = column[UUID]("uid", O.PrimaryKey, O.DBType("uuid"))
    def owner = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def permission = column[String]("permission",
      O.DBType("varchar(20)"), O.NotNull, O.Default[String]("read"))
    def created = column[DateTime]("created", O.DBType("timestamp"), O.NotNull)

    def * = (uid, owner, permission, created) <> (ApiKey.tupled, ApiKey.unapply)
  }

  val apiKeys = TableQuery[ApiKeys]

  def get(uid: UUID): Option[(String, String)] = {
    val l: List[(String, String)] = apiKeys.filter(_.uid === uid).
      map(k => (k.owner, k.permission)).list
    if (l.length == 1) {
      Some(l(1))
    } else {
      None
    }
  }

  def add(owner: String, permission: String): (StatusCode, String) = {
    var uid = UUID.randomUUID()
    while(get(uid) != None) {
      uid = UUID.randomUUID()
    }
    apiKeys.insert(ApiKey(uid, owner, permission, new DateTime())) match {
        case 0 => (InternalServerError, "Something went wrong")
        case n => (OK, uid.toString)
      }
  }

  def addReadWrite(owner: String): (StatusCode, String) = {
    val (statusRead, keyRead) = add(owner, "read")
    val (statusWrite, keyWrite) = add(owner, "write")
    if(statusRead == InternalServerError ||
      statusWrite == InternalServerError) {
        delete(UUID.fromString(keyRead))
        delete(UUID.fromString(keyWrite))
        (InternalServerError, "Something went wrong")
      } else {
        (OK, Map("read" -> keyRead, "write" -> keyWrite).toJson.compactPrint)
      }
  }
  
  def delete(uid: UUID): (StatusCode, String) =
    apiKeys.filter(_.uid === uid).delete match {
      case 0 => (NotFound, "Api key not found")
      case 1 => (OK, "Api key successfully deleted")
      case _ => (InternalServerError, "Something went wrong")
    }
}
