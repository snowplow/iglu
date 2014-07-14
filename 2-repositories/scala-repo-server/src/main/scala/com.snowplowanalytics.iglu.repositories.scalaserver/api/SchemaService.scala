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
package com.snowplowanalytics.iglu.repositories.scalaserver
package api

// This project
import core.SchemaActor._
import core.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

// Java
import java.util.UUID

// Scala
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
// for some reason mapTo doesnt work without it
import scala.reflect.ClassTag
import scala.util.{ Success, Failure }

// Spray
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.routing._

class SchemaService(schema: ActorRef, apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives {
  implicit val timeout = Timeout(5.seconds)

  val authenticator = TokenAuthenticator[String]("api-key") {
    key => (apiKey ? GetKey(UUID.fromString(key))).mapTo[Option[String]]
  }
  def auth: Directive1[String] = authenticate(authenticator)

  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r / "[a-zA-Z0-9_-]+".r / "[a-z]+".r /
    "[0-9]+-[0-9]+-[0-9]+".r) { (v, n, f, vs) => {
      auth { permission =>
        pathEnd {
          get {
            respondWithMediaType(`application/json`) {
              onComplete((schema ? GetSchema(v, n, f, vs)).
                mapTo[Option[String]]) {
                  case Success(opt) => complete {
                    opt match {
                      case Some(str) => str
                      case None => NotFound
                    }
                  }
                  case Failure(ex) => complete(InternalServerError,
                    s"An error occured: ${ex.getMessage}")
                }
            }
          }
        } ~
        anyParam('json)(json =>
          post {
            respondWithMediaType(`text/html`) {
              if (permission == "write") {
                onComplete((schema ? AddSchema(v, n, f, vs, json)).mapTo[Int]) {
                  case Success(status) => complete {
                    status match {
                      case 401 => (Unauthorized, "This schema already exists")
                      case 500 => (InternalServerError, "Something went wrong")
                      case 200 => (OK, "Schema added successfully")
                    }
                  }
                  case Failure(ex) => complete(InternalServerError,
                    s"An error occured: ${ex.getMessage}")
                }
              } else {
                complete(Unauthorized, "You do not have sufficient privileges")
              }
            }
          })
      }
    }}
  }
}
