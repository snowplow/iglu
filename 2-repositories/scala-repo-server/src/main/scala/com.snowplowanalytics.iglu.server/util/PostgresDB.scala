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
import model.{ SchemaDAO, ApiKeyDAO }
import IgluPostgresDriver.simple._

// Slick
import slick.jdbc.meta.MTable

object PostgresDB {
  val db = Database.forURL(
    url =
      s"jdbc:postgresql://${Config.pgHost}:${Config.pgPort}/${Config.pgDbName}",
    user = Config.pgUsername,
    password = Config.pgPassword,
    driver = Config.pgDriver
  )

  //def startPostgres = {
  //  if (MTable.getTables("schemas").list.isEmpty) {
  //    SchemaDAO.createTable
  //  }
  //  if (MTable.getTables("apikeys").list.isEmpty) {
  //    ApiKeyDAO.createTable
  //  }
  //}
}
