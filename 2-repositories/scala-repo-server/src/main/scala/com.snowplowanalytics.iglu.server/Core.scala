/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0,
* and you may not use this file except in compliance with the
* Apache License Version 2.0.
* You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the Apache License Version 2.0 is distributed on
* an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.  See the Apache License Version 2.0 for the specific
* language governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server

//This project
import actor.{ SchemaActor, ApiKeyActor }
import model.{ SchemaDAO, ApiKeyDAO }
import service.RoutedHttpService
import util.Config

// Akka
import akka.actor.{ ActorSystem, Props }
import akka.io.IO

// Spray
import spray.can.Http

// Slick
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.meta.MTable

trait Core {
  protected implicit def system: ActorSystem
}

trait BootedCore extends Core with Api {
  def system = ActorSystem("iglu-server")
  def actorRefFactory = system
  val rootService = system.actorOf(Props(new RoutedHttpService(routes)))

  Config.db withDynSession {
    if (MTable.getTables("schemas").list.isEmpty) {
      new SchemaDAO(Config.db).createTable
    }
    if (MTable.getTables("apikeys").list.isEmpty) {
      new ApiKeyDAO(Config.db).createTable
    }
  }

  IO(Http)(system) !
    Http.Bind(rootService, Config.interface, port = Config.port)

  sys.addShutdownHook(system.shutdown())
}

trait CoreActors {
  this: Core =>

  lazy val schema = system.actorOf(Props[SchemaActor])
  lazy val apiKey = system.actorOf(Props[ApiKeyActor])
}
