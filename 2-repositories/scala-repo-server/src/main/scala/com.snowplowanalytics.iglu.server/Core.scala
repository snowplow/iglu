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
import util.ServerConfig

// Akka
import akka.actor.{ ActorSystem, Props }
import akka.io.IO

// Argot
import org.clapper.argot._

// Config
import com.typesafe.config.{ ConfigFactory, Config }

// Java
import java.io.File

// Spray
import spray.can.Http

// Slick
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.meta.MTable

/**
 * This trait implements ``Core`` and starts the ``ActorSystem``
 * and starts a new ``RoutedHttpService`` with the routes defined in the
 * ``Api`` trait.
 * It also creates the necessary tables if they are not present in the
 * database.
 * It registers the termination handler to stop the actor system when the
 * JVM shuts down as well.
 */
object BootedCore extends App with Core with CoreActors with Api {

  // Command line argument parser
  val parser = new ArgotParser(
    programName = generated.Settings.name,
    compactUsage = true,
    preUsage = Some("%s: Version %s. Copyright (c) 2014, %s.".format(
      generated.Settings.name,
      generated.Settings.version,
      generated.Settings.organization)
    )
  )

  val config = parser.option[Config](List("config"), "filename",
    "Configuration file. Defaults to \"resources/application.conf\" " +
    "(within .jar) if not set") { (c, opt) =>
      val file = new File(c)
      if (file.exists) {
        ConfigFactory.parseFile(file)
      } else {
        parser.usage("Configuration file \"%s\" does not exist".format(c))
        ConfigFactory.empty
      }
    }
  parser.parse(args)

  // Creates a new ActorSystem
  def system = ActorSystem("iglu-server")
  def actorRefFactory = system

  // Starts a new http service
  val rootService = system.actorOf(Props(new RoutedHttpService(routes)))

  // Creates the necessary table is they are not already present in the
  // database
  ServerConfig.db withDynSession {
    if (MTable.getTables("schemas").list.isEmpty) {
      new SchemaDAO(ServerConfig.db).createTable
    }
    if (MTable.getTables("apikeys").list.isEmpty) {
      new ApiKeyDAO(ServerConfig.db).createTable
    }
  }

  // Starts the server
  IO(Http)(system) !
    Http.Bind(rootService, ServerConfig.interface, port = ServerConfig.port)

  // Register the termination handler for when the JVM shuts down
  sys.addShutdownHook(system.shutdown())
}

trait Core {
  protected implicit def system: ActorSystem
}

/**
 * Core actors maintains a reference to the different actors
 */
trait CoreActors {
  this: Core =>

  lazy val schemaActor = system.actorOf(Props[SchemaActor])
  lazy val apiKeyActor = system.actorOf(Props[ApiKeyActor])
}
