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

// Java
import java.util.UUID

// Joda
import org.joda.time.LocalDateTime

// Json4s
import org.json4s.jackson.Serialization.writePretty

// Slick
import Database.dynamicSession

// Spray
import spray.http.StatusCode
import spray.http.StatusCodes._

class ApiKeyDAO(val db: Database) extends DAO {

  private val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  case class ApiKey(
    uid: UUID,
    owner: String,
    permission: String,
    created: LocalDateTime
  )

  class ApiKeys(tag: Tag) extends Table[ApiKey](tag, "apikeys") {
    def uid = column[UUID]("uid", O.PrimaryKey, O.DBType("uuid"))
    def owner = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def permission = column[String]("permission",
      O.DBType("varchar(20)"), O.NotNull, O.Default[String]("read"))
    def created = column[LocalDateTime]("created", O.DBType("timestamp"),
      O.NotNull)

    def * = (uid, owner, permission, created) <> (ApiKey.tupled, ApiKey.unapply)
  }

  val apiKeys = TableQuery[ApiKeys]

  def createTable = db withDynSession { apiKeys.ddl.create }
  def dropTable = db withDynSession { apiKeys.ddl.drop }

  def get(uid: String): Option[(String, String)] = {
    if (uid matches uidRegex) {
      val uuid = UUID.fromString(uid)
      db withDynSession {
        val l: List[(String, String)] = apiKeys.filter(_.uid === uuid).
          map(k => (k.owner, k.permission)).list
        if (l.length == 1) {
          Some(l(0))
        } else {
          None
        }
      }
    } else {
      None
    }
  }

  private def validate(owner: String, permission: String): Boolean = {
    db withDynSession {
      val l: List[(String, String)] =
        apiKeys.map(a => (a.owner, a.permission)).list
      val owners = l.filter(a => (a._1 == owner && a._2 != permission))
      val startWithOwners = l.filter(o =>
          ((o._1.startsWith(owner) || owner.startsWith(o._1)) && o._1 != owner))

      if (startWithOwners.length > 0 || owners.length > 1) {
        false
      } else {
        true
      }
    }
  }

  private def add(owner: String, permission: String): (StatusCode, String) = {
    db withDynSession {
      if(validate(owner, permission)) {
        val uid = UUID.randomUUID()
        apiKeys.insert(
          ApiKey(uid, owner, permission, new LocalDateTime())) match {
            case 0 => (InternalServerError, "Something went wrong")
            case n => (OK, uid.toString)
          }
      } else {
        (Unauthorized, "This vendor is conflicting with an existing one")
      }
    }
  }

  def addReadWrite(owner: String): (StatusCode, String) = {
    db withDynSession {
      val (statusRead, keyRead) = add(owner, "read")
      if (statusRead == Unauthorized) {
        (statusRead, result(401, keyRead))
      } else {
        val (statusWrite, keyWrite) = add(owner, "write")
        if(statusRead == InternalServerError ||
          statusWrite == InternalServerError) {
            delete(UUID.fromString(keyRead))
            delete(UUID.fromString(keyWrite))
            (InternalServerError, result(500, "Something went wrong"))
          } else {
            (OK, writePretty(Map("read" -> keyRead, "write" -> keyWrite)))
          }
      }
    }
  }
  
  def delete(uid: UUID): (StatusCode, String) =
    db withDynSession {
      apiKeys.filter(_.uid === uid).delete match {
        case 0 => (NotFound, result(404, "Api key not found"))
        case 1 => (OK, result(200, "Api key successfully deleted"))
        case _ => (InternalServerError, result(500, "Something went wrong"))
      }
    }
}
