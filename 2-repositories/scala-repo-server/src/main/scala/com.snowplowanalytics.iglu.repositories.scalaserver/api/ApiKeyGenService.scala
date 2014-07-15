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
import core.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Java
import java.util.UUID

// Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.MediaTypes._
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.routing._

class ApiKeyGenService(apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  private val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  val authenticator = TokenAuthenticator[(String, String)]("api-key") {
    key => (apiKey ? GetKey(UUID.fromString(key))).
      mapTo[Option[(String, String)]]
  }
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  val route = rejectEmptyResponse {
    path("apikeygen") {
      respondWithMediaType(`application/json`) {
        auth { authTuple =>
          if(authTuple._2 == "super") {
            anyParams('owner)(owner =>
              validate(owner matches "[a-z.]+", "Invalid owner") {
                post {
                  complete {
                    (apiKey ? AddBothKey(owner)).
                      mapTo[(StatusCode, String)]
                  }
                }
              }
            ) ~
            anyParams('key)(key =>
              validate(key matches uidRegex, "Invalid regex") {
                delete {
                  complete {
                    (apiKey ? DeleteKey(UUID.fromString(key))).
                      mapTo[(StatusCode, String)]
                  }
                }
              }
            )
          } else {
            complete(Unauthorized, "You do not have sufficient privileges")
          }
        }
      }
    }
  }
}
