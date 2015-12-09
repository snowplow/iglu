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
package util

// This project
import IgluPostgresDriver.simple._

// Scala
import scala.collection.JavaConverters._

// Config
import com.typesafe.config.ConfigFactory

/**
 * Config object getting the information stored in application.conf
 */
object ServerConfig {

  val config = ConfigFactory.load

  val env = System.getenv.asScala.lift

  //Interface on which the server will be running
  val interface = config.getString("repo-server.interface")
  //Port on which the server will be running
  val port = env("PORT").map(_.toInt) getOrElse config.getInt("repo-server.port")

  val pgHost = env("RDS_HOSTNAME") getOrElse config.getString("postgres.host")
  val pgPort = env("RDS_PORT").map(_.toInt) getOrElse config.getInt("postgres.port")
  val pgDbName = env("RDS_DB_NAME") getOrElse config.getString("postgres.dbname")
  val pgUsername = env("RDS_USERNAME") getOrElse config.getString("postgres.username")
  val pgPassword = env("RDS_PASSWORD") getOrElse config.getString("postgres.password")
  val pgDriver = config.getString("postgres.driver")

  //Reference to the database
  val db = Database.forURL(
    url = s"jdbc:postgresql://${pgHost}:${pgPort}/${pgDbName}",
    user = pgUsername,
    password = pgPassword,
    driver = pgDriver
  )
}
