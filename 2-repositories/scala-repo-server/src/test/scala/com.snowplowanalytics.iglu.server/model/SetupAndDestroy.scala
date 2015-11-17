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

// This project
import util.ServerConfig

// Slick
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{ StaticQuery => Q }

trait SetupAndDestroy extends BeforeAndAfterAll {
  private val dbName = getClass.getSimpleName.toLowerCase

  private val db = Database.forURL(
    url =  s"jdbc:postgresql://${ServerConfig.pgHost}:${ServerConfig.pgPort}/" +
      s"${ServerConfig.pgDbName}",
    user = ServerConfig.pgUsername,
    password = ServerConfig.pgPassword,
    driver = ServerConfig.pgDriver
  )

  def beforeAll() {
    db withDynSession {
      Q.updateNA(s"drop database if exists ${dbName};").execute
      Q.updateNA(s"create database ${dbName};").execute
    }
  }

  def afterAll() {
    db withDynSession {
      Q.updateNA(s"drop database ${dbName};").execute
    }
  }

  val database = Database.forURL(
    url = s"jdbc:postgresql://${ServerConfig.pgHost}:${ServerConfig.pgPort}/" +
      s"${dbName}",
    user = ServerConfig.pgUsername,
    password = ServerConfig.pgPassword,
    driver = ServerConfig.pgDriver
  )
}
