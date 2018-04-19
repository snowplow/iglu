/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
package util

// This project
import IgluPostgresDriver.simple._

// Scala
import scala.collection.JavaConverters._

// Config
import com.typesafe.config.Config


case class ServerConfig(config: Config) {

  val env = System.getenv.asScala.lift

  //Interface on which the server will be running
  val interface = env("IGLU_INTERFACE") getOrElse config.getString("repo-server.interface")
  // baseUrl of Swagger UI
  val baseURL = env("IGLU_SWAGGER_UI_BASE_URL") getOrElse config.getString("repo-server.baseURL")
  //Port on which the server will be running
  val port = env("IGLU_PORT").map(_.toInt) getOrElse config.getInt("repo-server.port")

  val pgHost = env("IGLU_PG_HOSTNAME") getOrElse config.getString("postgres.host")
  val pgPort = env("IGLU_PG_PORT").map(_.toInt) getOrElse config.getInt("postgres.port")
  val pgDbName = env("IGLU_PG_DBNAME") getOrElse config.getString("postgres.dbname")
  val pgUsername = env("IGLU_PG_USERNAME") getOrElse config.getString("postgres.username")
  val pgPassword = env("IGLU_PG_PASSWORD") getOrElse config.getString("postgres.password")
  val pgDriver = config.getString("postgres.driver")

  //Reference to the database
  val db: Database = Database.forURL(
    url = s"jdbc:postgresql://$pgHost:$pgPort/$pgDbName",
    user = pgUsername,
    password = pgPassword,
    driver = pgDriver
  )
}
