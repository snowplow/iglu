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

// Scala
import scala.annotation.meta.field

// Java
import java.util.UUID

// Joda
import org.joda.time.LocalDateTime

// Json4s
import org.json4s.jackson.Serialization.writePretty

// Slick
import Database.dynamicSession

// Akka Http
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._

// Swagger
import io.swagger.annotations.{ApiModel, ApiModelProperty}


/**
  * Case class representing an API key in the database.
  * @constructor create an API key object from required data
  * @param uid API key uuid serving as primary key
  * @param vendorPrefix of the API key
  * @param permission API key permission in (read, write, super)
  * @param createdAt date at which point the API key was created
  */
@ApiModel(value = "ApiKey", description = "represents an API key in the database")
case class ApiKey(
                   @(ApiModelProperty @field)(value = "Unique identifier of the key")
                   uid: UUID,
                   @(ApiModelProperty @field)(value = "Vendor of the key")
                   vendorPrefix: String,
                   @(ApiModelProperty @field)(value = "Permission of the key")
                   permission: String,
                   @(ApiModelProperty @field)(value = "Date at which this key was created", hidden = true)
                   createdAt: LocalDateTime
                 )

/**
  * DAO for accessing the apikeys table in the database
  * @constructor create an API key DAO with a reference to the database
  * @param db a reference to a ``Database``
  */
class ApiKeyDAO(val db: Database) extends DAO {

  private val uidRegex =
  "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  /**
    * Schema for the apikeys table.
    */
  class ApiKeys(tag: Tag) extends Table[ApiKey](tag, "apikeys") {
    def uid = column[UUID]("uid", O.PrimaryKey, O.DBType("uuid"))
    def vendorPrefix = column[String]("vendor_prefix", O.DBType("varchar(200)"),
      O.NotNull)
    def permission = column[String]("permission",
      O.DBType("varchar(20)"), O.NotNull, O.Default[String]("read"))
    def createdAt = column[LocalDateTime]("createdat", O.DBType("timestamp"),
      O.NotNull)

    def * = (uid, vendorPrefix, permission, createdAt) <>
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
    * @return an option containing a (vendorPrefix, permission) pair
    */
  def get(uid: String): Option[(String, String)] = {
    if (uid matches uidRegex) {
      val uuid = UUID.fromString(uid)
      db withDynSession {
        val l: List[(String, String)] =
          apiKeys
            .filter(_.uid === uuid)
            .map(k => (k.vendorPrefix, k.permission))
            .list
        if (l.length == 1) {
          Some(l(0))
        } else {
          Some("-" -> "-")
        }
      }
    } else {
      Some("-" -> "-")
    }
  }

  /**
    * Validates that a new vendorPrefix is not conflicting with an existing one
    * (same prefix).
    * @param vendorPrefix vendorPrefix of the new API keys being validated
    * @return a boolean indicating whether or not we allow this new API key
    * vendor prefix
    */
  private def validate(vendorPrefix: String): Boolean =
    db withDynSession {
      apiKeys
        .map(_.vendorPrefix)
        .list
        .filter(o => (o.startsWith(vendorPrefix) || vendorPrefix.startsWith(o) ||
          o == vendorPrefix) && o != "*")
        .length == 0
    }

  /**
    * Adds a new API key.
    * @param vendorPrefix vendorPrefix of the new API key
    * @param permission permission of the new API key
    * @return a status code and a json response pair
    */
  private def add(vendorPrefix: String, permission: String):
  (StatusCode, String) =
    db withDynSession {
      val uid = UUID.randomUUID()
      apiKeys.insert(
        ApiKey(uid, vendorPrefix, permission, new LocalDateTime())) match {
        case 0 => (InternalServerError, "Something went wrong")
        case n => (OK, uid.toString)
      }
    }

  /**
    * Adds both read and write API keys for a vendor prefix after validating it.
    * @param vendorPrefix vendorPrefix of the new pair of keys
    * @return a status code and a json containing the pair of API keys.
    */
  def addReadWrite(vendorPrefix: String): (StatusCode, String) =
    db withDynSession {
      if (validate(vendorPrefix)) {
        val (statusRead, keyRead) = add(vendorPrefix, "read")
        val (statusWrite, keyWrite) = add(vendorPrefix, "write")

        if(statusRead == InternalServerError ||
          statusWrite == InternalServerError) {
          delete(keyRead)
          delete(keyWrite)
          (InternalServerError, result(500, "Something went wrong"))
        } else {
          (Created, writePretty(Map("read" -> keyRead, "write" -> keyWrite)))
        }
      } else {
        (Unauthorized, "This vendor prefix is conflicting with an existing one")
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
    * Deletes all API keys having to the specified vendor prefix.
    * @param vendorPrefix vendor prefix of the API keys we want to delete
    * @return a (status code, json response) pair
    */
  def deleteFromVendorPrefix(vendorPrefix: String): (StatusCode, String) =
    db withDynSession {
      apiKeys.filter(_.vendorPrefix === vendorPrefix).delete match {
        case 0 => (NotFound, result(404, "Vendor prefix not found"))
        case 1 => (OK, result(200, "API key deleted for " + vendorPrefix))
        case n => (OK, result(200, "API keys deleted for " + vendorPrefix))
      }
    }
}
