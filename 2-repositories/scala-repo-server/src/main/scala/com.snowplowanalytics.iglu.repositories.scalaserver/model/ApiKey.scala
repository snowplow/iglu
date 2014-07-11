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

// Java
import java.util.UUID

// Slick
import scala.slick.driver.PostgresDriver
import scala.slick.driver.PostgresDriver.simple._
import scala.slick.lifted.Tag

// Spray
import spray.json.DefaultJsonProtocol._

case class ApiKey(
  uid: UUID,
  owner: String,
  permission: String
  //created: DateTime
)

object ApiKeyDAO {
  class ApiKeyTable(tag: Tag) extends Table[ApiKey](tag, "apikeys") {
    def uid = column[UUID]("uid", O.PrimaryKey, O.DBType("uuid"))
    def owner = column[String]("vendor", O.DBType("varchar(200)"), O.NotNull)
    def permission = column[String]("permission",
      O.DBType("varchar(20)"), O.NotNull, O.Default[String]("read"))
    //def created = column[DateTime]("created", ).DBType("timestamp"), O.NotNull)

    def * = (uid ~ owner ~ permission) <> (ApiKey, ApiKey.unpply)

    def forInsert = (owner ~ permission) returning uid
  }

  def get(uid: UUID): Option[String] = {
    val l = Query(ApiKeyTable).where(_.uid is uid).list
    if (l.count == 1) {
      Some(l.toJson.compactPrint)
    } else {
      None
    }
  }

  //dunno if I should generate them here or in the associated actor
  def add(owner: String, permission: String): String =
    ApiKeyTable.forInsert.insert(owner, permission)
}
