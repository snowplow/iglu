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

// Akka
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

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

class SchemaService(schema: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives {
  implicit val timeout = Timeout(5.seconds)

  val apiKeyStore = DynamoFactory.apiKeyStore
  val authenticator = TokenAuthenticator[String]("api-key") {
    key => util.FutureConverter.fromTwitter(apiKeyStore.get(key))
  }
  def auth: Directive1[String] = authenticate(authenticator)

  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r / "[a-zA-Z0-9_-]+".r / "[a-z]+".r /
    "[0-9]+-[0-9]+-[0-9]+".r) { (vendor, name, format, version) => {
      val key = s"${vendor}/${name}/${format}/${version}"
      auth { permission =>
        pathEnd {
          get {
            respondWithMediaType(`application/json`) {
              onComplete((schema ? Get(key)).mapTo[Option[String]]) {
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
                onComplete((schema ? Get(key)).mapTo[Option[String]]) {
                  case Success(opt) => complete {
                    opt match {
                      case Some(str) => (Unauthorized,
                        "This schema already exists")
                      case None => {
                        schema ! Put((key, Some(json)))
                        (OK, "Success")
                      }
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
