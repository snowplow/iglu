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
import actor.SchemaActor._
import actor.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.routing._

/**
 * Service to interact with schemas.
 * @constructor create a new schema service with a schema and apiKey actors
 * @param schema a reference to a ``SchemaActor``
 * @param apiKey a reference to a ``ApiKeyActor``
 */
class SchemaService(schema: ActorRef, apiKey: ActorRef)
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
   * Directive to validate the json provided (either by query param or form
   * data) is self-describing.
   */
  def validateJson: Directive1[String] =
    anyParam('json) flatMap { json =>
      onSuccess((schema ? Validate(json)).mapTo[(StatusCode, String)]) flatMap {
        ext =>
          ext match {
            case (OK, j) => provide(j)
            case res => complete(res)
          }
      }
    }

  /**
   * Route handled by the schema service.
   */
  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r / "[a-zA-Z0-9_-]+".r / "[a-z]+".r /
    "[0-9]+-[0-9]+-[0-9]+".r) { (v, n, f, vs) => {
      auth { authTuple =>
        respondWithMediaType(`application/json`) {
          if (v startsWith authTuple._1) {
            get {
              anyParam('filter.?) { filter =>
                filter match {
                  case Some("metadata") => complete {
                    (schema ? GetMetadata(v, n, f, vs)).
                      mapTo[(StatusCode, String)]
                  }
                  case _ => complete {
                    (schema ? GetSchema(v, n, f, vs)).
                      mapTo[(StatusCode, String)]
                  }
                }
              }
            } ~
            validateJson { json =>
              post {
                if (authTuple._2 == "write") {
                  complete {
                    (schema ? AddSchema(v, n, f, vs, json)).
                      mapTo[(StatusCode, String)]
                  }
                } else {
                  complete(Unauthorized,
                    "You do not have sufficient privileges")
                }
              }
            }
          } else {
            complete(Unauthorized, "You do not have sufficient privileges")
          }
        }
      }
    }}
  }
}
