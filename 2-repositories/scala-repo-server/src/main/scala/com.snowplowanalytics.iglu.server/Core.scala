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
import service.RoutedHttpService
import util.{ PostgresDB, Config }

// Akka
import akka.actor.{ ActorSystem, Props }
import akka.io.IO

// Spray
import spray.can.Http

trait Core {
  protected implicit def system: ActorSystem
}

trait BootedCore extends Core with Api with PostgresDB {
  def system = ActorSystem("scala-repo-server")
  def actorRefFactory = system
  val rootService = system.actorOf(Props(new RoutedHttpService(routes)))

  startPostgres

  IO(Http)(system) !
    Http.Bind(rootService, Config.interface, port = Config.port)

  sys.addShutdownHook(system.shutdown())
}

trait CoreActors {
  this: Core =>

  lazy val schema = system.actorOf(Props[SchemaActor])
  lazy val apiKey = system.actorOf(Props[ApiKeyActor])
}
