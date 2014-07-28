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

//Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.StatusCodes._
import spray.http.StatusCode
import spray.http.MediaTypes._
import spray.routing._

/**
 * Service to retrieve multiple schemas in one request.
 * @constructor create a new catalog service with a schema and apiKey actors
 * @param schema a reference to a ``SchemaActor``
 * @param apiKey a reference to a ``ApiKeyActor``
 */
class CatalogService(schema: ActorRef, apiKey: ActorRef)
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
   * Route handled by the catalog service.
   */
  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r) { v => {
      auth { authTuple =>
        if (v startsWith authTuple._1) {
          respondWithMediaType(`application/json`) {
            get {
              pathPrefix("[a-zA-Z0-9_-]+".r) { n => {
                pathPrefix("[a-z]+".r) { f => {
                  pathEnd {
                    complete {
                      (schema ? GetSchemasFromFormat(v, n, f)).
                        mapTo[(StatusCode, String)]
                    }
                  }
                }} ~
                pathEnd {
                  complete {
                    (schema ? GetSchemasFromName(v, n)).
                      mapTo[(StatusCode, String)]
                  }
                }
              }} ~
              pathEnd {
                complete {
                  (schema ? GetSchemasFromVendor(v)).mapTo[(StatusCode, String)]
                }
              }
            }
          }
        } else {
          complete(Unauthorized, "You do not have sufficient privileges")
        }
      }
    }}
  }
}
