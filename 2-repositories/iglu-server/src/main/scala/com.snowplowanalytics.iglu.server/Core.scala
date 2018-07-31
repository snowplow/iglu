/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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

// Scala
import scala.concurrent.ExecutionContextExecutor

//This project
import actor.{ SchemaActor, ApiKeyActor }
import model.{ SchemaDAO, ApiKeyDAO }
import util.ServerConfig
import util.IgluPostgresDriver.simple._

// Akka
import akka.actor.{ ActorSystem, ActorRef, Props }
import akka.stream.ActorMaterializer

// Akka Http
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._

// Slick
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.meta.MTable

// slf4j
import org.slf4j.{Logger, LoggerFactory}

// postgres
import org.postgresql.util.PSQLException


class IgluServer(serverConfig: ServerConfig) extends Api {

  implicit val system: ActorSystem = ActorSystem("iglu-server")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // create root-level actors with provided config
  val schemaActor: ActorRef = system.actorOf(Props(classOf[SchemaActor], serverConfig), "schemaActor")
  val apiKeyActor: ActorRef = system.actorOf(Props(classOf[ApiKeyActor], serverConfig), "apiKeyActor")

  lazy val log: Logger = LoggerFactory.getLogger(getClass)

  def start(): Unit = {
    // Creates the necessary table if they are not already present in the database
    TableInitialization.initializeTables(serverConfig.db)

    // Starts the server
    Http().bindAndHandle(routes ~ new SwaggerDocService(serverConfig).routes,
                        serverConfig.interface, serverConfig.port)
      .map { binding =>
        log.info(s"REST interface bound to ${binding.localAddress}")
      } recover { case ex =>
        log.error("REST interface could not be bound to " +
                  s"${serverConfig.interface}:${serverConfig.port}", ex.getMessage)
      }
  }
}

object TableInitialization {
  // Creates the necessary table if they are not already present in the database
  def initializeTables(db: Database): Unit = {
    try {
      db withDynSession {
        if (MTable.getTables("schemas").list.isEmpty) {
          new SchemaDAO(db).createTable()
        }
        new SchemaDAO(db).bootstrapSelfDescSchema()
        if (MTable.getTables("apikeys").list.isEmpty) {
          new ApiKeyDAO(db).createTable
        }
      }
    }
    catch {
      case err: PSQLException =>
        println("There is a problem with database initialization: " + err.getMessage + " Check your credentials.")
        sys.exit(1)
      case _: Throwable =>
        println("Something went wrong with database initialization.")
        sys.exit(1)
    }
  }
}
