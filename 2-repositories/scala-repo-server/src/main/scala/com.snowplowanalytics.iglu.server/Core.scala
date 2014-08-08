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

/**
 * This trait implements ``Core`` and starts the ``ActorSystem``
 * and starts a new ``RoutedHttpService`` with the routes defined in the
 * ``Api`` trait.
 * It also creates the necessary tables if they are not present in the
 * database.
 * It registers the termination handler to stop the actor system when the
 * JVM shuts down as well.
 */
trait BootedCore extends Core with Api {

  // Creates a new ActorSystem
  def system = ActorSystem("iglu-server")
  def actorRefFactory = system

  // Starts a new http service
  val rootService = system.actorOf(Props(new RoutedHttpService(routes)))

  // Creates the necessary table is they are not already present in the
  // database
  Config.db withDynSession {
    if (MTable.getTables("schemas").list.isEmpty) {
      new SchemaDAO(Config.db).createTable
    }
    if (MTable.getTables("apikeys").list.isEmpty) {
      new ApiKeyDAO(Config.db).createTable
    }
  }

  // Starts the server
  IO(Http)(system) !
    Http.Bind(rootService, Config.interface, port = Config.port)

  // Register the termination handler for when the JVM shuts down
  sys.addShutdownHook(system.shutdown())
}

/**
 * Core actors maintains a reference to the different actors
 */
trait CoreActors {
  this: Core =>

  lazy val schema = system.actorOf(Props[SchemaActor])
  lazy val apiKey = system.actorOf(Props[ApiKeyActor])
}
