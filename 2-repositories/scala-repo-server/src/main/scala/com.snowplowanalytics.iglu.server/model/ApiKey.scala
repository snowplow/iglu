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

/**
 * DAO for accessing the apikeys table in the database
 * @constructor create an api key DAO with a reference to the database
 * @param db a reference to a ``Database``
 */
class ApiKeyDAO(val db: Database) extends DAO {

  private val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  /**
   * Case class representing an api key in the database.
   * @constructor create an api key object from required data
   * @param uid api key uuid serving as primary key
   * @param owner of the api key
   * @param permission api key permission in (read, write, super)
   * @param createdAt date at which point the api key was created
   */
  case class ApiKey(
    uid: UUID,
    owner: String,
    permission: String,
    createdAt: LocalDateTime
  )

  /**
   * Schema for the apikeys table.
   */
  class ApiKeys(tag: Tag) extends Table[ApiKey](tag, "apikeys") {
    def uid = column[UUID]("uid", O.PrimaryKey, O.DBType("uuid"))
    def owner = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def permission = column[String]("permission",
      O.DBType("varchar(20)"), O.NotNull, O.Default[String]("read"))
    def createdAt = column[LocalDateTime]("createdat", O.DBType("timestamp"),
      O.NotNull)

    def * = (uid, owner, permission, createdAt) <>
      (ApiKey.tupled, ApiKey.unapply)
  }

  //Object used to access the table
  val apiKeys = TableQuery[ApiKeys]

  /**
   * Creates the apikeys table.
   */
  def createTable = db withDynSession { apiKeys.ddl.create }

  /**
   * Deletes the apikeys table.
   */
  def dropTable = db withDynSession { apiKeys.ddl.drop }

  /**
   * Gets an api key from an uuid.
   * @param uid the api key's uuid
   * @return an option containing a (owner, permission) pair
   */
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

  /**
   * Validates that a new owner is not conflicting with an existing one
   * (same prefix).
   * @param owner owner of the new api keys being validated
   * @return a boolean indicating whether or not we allow this new api key owner
   */
  private def validate(owner: String): Boolean =
    db withDynSession {
      apiKeys.map(_.owner).list.
        filter(o => o.startsWith(owner) || owner.startsWith(o) || o == owner).
        length == 0
    }

  /**
   * Adds a new api key.
   * @param owner owner of the new api key
   * @param permission permission of the new api key
   * @return a status code and a json response pair
   */
  private def add(owner: String, permission: String): (StatusCode, String) = {
    db withDynSession {
      val uid = UUID.randomUUID()
      apiKeys.insert(
        ApiKey(uid, owner, permission, new LocalDateTime())) match {
          case 0 => (InternalServerError, "Something went wrong")
          case n => (OK, uid.toString)
        }
    }
  }

  /**
   * Adds both read and write api keys for an owner after validating it.
   * @param owner owner of the new pair of keys
   * @returns a status code and a json containing the pair of api keys.
   */
  def addReadWrite(owner: String): (StatusCode, String) = {
    db withDynSession {
      if (validate(owner)) {
        val (statusRead, keyRead) = add(owner, "read")
        val (statusWrite, keyWrite) = add(owner, "write")

        if(statusRead == InternalServerError ||
          statusWrite == InternalServerError) {
            delete(keyRead)
            delete(keyWrite)
            (InternalServerError, result(500, "Something went wrong"))
          } else {
            (OK, writePretty(Map("read" -> keyRead, "write" -> keyWrite)))
          }
      } else {
        (Unauthorized, "This vendor is conflicting with an existing one")
      }
    }
  }

  /**
   * Deletes an api key from its uuid.
   * @param uid the api key's uuid
   * @return a status code and json response pair
   */
  def delete(uid: String): (StatusCode, String) =
    if (uid matches uidRegex) {
      db withDynSession {
        apiKeys.filter(_.uid === UUID.fromString(uid)).delete match {
          case 0 => (NotFound, result(404, "Api key not found"))
          case 1 => (OK, result(200, "Api key successfully deleted"))
          case _ => (InternalServerError, result(500, "Something went wrong"))
        }
      }
    } else {
      (Unauthorized, result(401, "The api key provided is not an UUID"))
    }

  /**
   * Deletes all api keys belonging to the specified owner.
   * @param owner ownere of the api keys we want to delete
   * @return a (status code, json response) pair
   */
  def deleteFromOwner(owner: String): (StatusCode, String) =
    db withDynSession {
      apiKeys.filter(_.owner === owner).delete match {
        case 0 => (NotFound, result(404, "Owner not found"))
        case 1 => (OK, result(200, "Api key deleted for " + owner))
        case n => (OK, result(200, n + " api keys deleted for " + owner))
      }
    }
}
