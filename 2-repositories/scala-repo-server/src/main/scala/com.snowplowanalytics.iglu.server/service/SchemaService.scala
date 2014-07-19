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

// Java
import java.util.UUID

// Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.routing._

class SchemaService(schema: ActorRef, apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  val authenticator = TokenAuthenticator[(String, String)]("api-key") {
    key => (apiKey ? GetKey(UUID.fromString(key))).
      mapTo[Option[(String, String)]]
  }
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r / "[a-zA-Z0-9_-]+".r / "[a-z]+".r /
    "[0-9]+-[0-9]+-[0-9]+".r) { (v, n, f, vs) => {
      auth { authTuple =>
        if (v startsWith authTuple._1) {
          pathEnd {
            get {
              respondWithMediaType(`application/json`) {
                complete {
                  (schema ? GetSchema(v, n, f, vs)).mapTo[(StatusCode, String)]
                }
              }
            }
          } ~
          anyParam('json)(json =>
            post {
              respondWithMediaType(`application/json`) {
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
            })
        } else {
          complete(Unauthorized, "You do not have sufficient privileges")
        }
      }
    }}
  }
}
