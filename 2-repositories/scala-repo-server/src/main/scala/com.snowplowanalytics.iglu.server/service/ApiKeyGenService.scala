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
package service

// This project
import actor.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.MediaTypes._
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.routing._

/**
 * Service to interact with api keys.
 * @constructor create a new api key generation service with an apiKey actor
 * @param apiKey a reference to a ``ApiKeyActor``
 */
class ApiKeyGenService(apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Creates a ``TokenAuthenticator`` to extract the api-key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api-key") {
    key => (apiKey ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user using the authenticator.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  /**
   * Route handled by the api key generation service.
   */
  val route = rejectEmptyResponse {
    path("keygen") {
      respondWithMediaType(`application/json`) {
        auth { authTuple =>
          if(authTuple._2 == "super") {
            anyParams('owner)(owner =>
              validate(owner matches "[a-z.]+", "Invalid owner") {
                post {
                  complete {
                    (apiKey ? AddBothKey(owner)).mapTo[(StatusCode, String)]
                  }
                } ~
                delete {
                  complete {
                    (apiKey ? DeleteKeys(owner)).mapTo[(StatusCode, String)]
                  }
                }
              }
            ) ~
            anyParams('key)(key =>
              delete {
                complete {
                  (apiKey ? DeleteKey(key)).mapTo[(StatusCode, String)]
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
