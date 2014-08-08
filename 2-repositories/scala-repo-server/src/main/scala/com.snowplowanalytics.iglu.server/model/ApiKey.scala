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
 * @constructor create an API key DAO with a reference to the database
 * @param db a reference to a ``Database``
 */
class ApiKeyDAO(val db: Database) extends DAO {

  private val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  /**
   * Case class representing an API key in the database.
   * @constructor create an API key object from required data
   * @param uid API key uuid serving as primary key
   * @param owner of the API key
   * @param permission API key permission in (read, write, super)
   * @param createdAt date at which point the API key was created
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
    def owner = column[String]("owner", O.DBType("varchar(200)"), O.NotNull)
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
   * Gets an API key from an uuid.
   * @param uid the API key's uuid
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
   * Validates that when adding a key it is not conflicting with an existing
   * one. In particular, checks that there is not API key with the same (owner,
   * permission) pair and that there is no owner already in the database with
   * the same prefix.
   * @param owner owner of the new API key being validated
   * @param permission permission of the new API key being validated
   * @return a boolean indicating whether or not we allow this new API key
   */
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

  /**
   * Adds a new API key after validating it.
   * @param owner owner of the new API key
   * @param permission permission of the new API key
   * @return a status code and a json response pair
   */
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
        (Unauthorized, "This owner is conflicting with an existing one")
      }
    }
  }

  /**
   * Adds both read and write API keys for an owner.
   * @param owner owner of the new pair of keys
   * @returns a status code and a json containing the pair of API keys.
   */
  def addReadWrite(owner: String): (StatusCode, String) = {
    db withDynSession {
      val (statusRead, keyRead) = add(owner, "read")
      if (statusRead == Unauthorized) {
        (statusRead, result(401, keyRead))
      } else {
        val (statusWrite, keyWrite) = add(owner, "write")
        if(statusRead == InternalServerError ||
          statusWrite == InternalServerError) {
            delete(keyRead)
            delete(keyWrite)
            (InternalServerError, result(500, "Something went wrong"))
          } else {
            (OK, writePretty(Map("read" -> keyRead, "write" -> keyWrite)))
          }
      }
    }
  }

  /**
   * Deletes an API key from its uuid.
   * @param uid the API key's uuid
   * @return a status code and json response pair
   */
  def delete(uid: String): (StatusCode, String) =
    if (uid matches uidRegex) {
      db withDynSession {
        apiKeys.filter(_.uid === UUID.fromString(uid)).delete match {
          case 0 => (NotFound, result(404, "API key not found"))
          case 1 => (OK, result(200, "API key successfully deleted"))
          case _ => (InternalServerError, result(500, "Something went wrong"))
        }
      }
    } else {
      (Unauthorized, result(401, "The API key provided is not an UUID"))
    }

  /**
   * Deletes all API keys belonging to the specified owner.
   * @param owner ownere of the API keys we want to delete
   * @return a (status code, json response) pair
   */
  def deleteFromOwner(owner: String): (StatusCode, String) =
    db withDynSession {
      apiKeys.filter(_.owner === owner).delete match {
        case 0 => (NotFound, result(404, "Owner not found"))
        case 1 => (OK, result(200, "API key deleted for " + owner))
        case n => (OK, result(200, "API keys deleted for " + owner))
      }
    }
}
