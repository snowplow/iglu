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

// Scala
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.control.NonFatal

//This project
import actor.{ SchemaActor, ApiKeyActor }
import model.{ SchemaDAO, ApiKeyDAO }
import util.ServerConfig._

// Akka
import akka.event.Logging
import akka.actor.{ ActorSystem, ActorRef, Props }
import akka.stream.ActorMaterializer

// Akka Http
import akka.http.scaladsl.Http

// Slick
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.meta.MTable


trait BootedCore extends Core with CoreActors with Api {

  implicit val system: ActorSystem = ActorSystem("iglu-server")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val logger = Logging(system, getClass)

  try {
    // Creates the necessary table if they are not already present in the database
    TableInitialization.initializeTables()

    // Starts the server
    val bindingFuture = Http().bindAndHandle(routes, interface, port)

    println(s"Server online at http://$interface:$port/\nPress RETURN to stop...")
    StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  } catch {
    case NonFatal(nf) =>
      nf.printStackTrace()
      // Necessary to stop app from hanging indefinitely
      System.exit(1)
  }
}

object TableInitialization {
  // Creates the necessary table is they are not already present in the
  // database
  def initializeTables(): Unit = {
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
}

trait Core {
  protected implicit def system: ActorSystem
}

/**
  * Core actors maintains a reference to the different actors
  */
trait CoreActors {
  this: Core =>
    lazy val schemaActor: ActorRef = system.actorOf(Props[SchemaActor], "schemaActor")
    lazy val apiKeyActor: ActorRef = system.actorOf(Props[ApiKeyActor], "apiKeyActor")
}
