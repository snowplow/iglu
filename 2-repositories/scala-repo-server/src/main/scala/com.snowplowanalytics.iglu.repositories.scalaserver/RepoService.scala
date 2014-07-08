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

// Akka
import akka.actor.Actor

// Scala
import scala.concurrent.ExecutionContext.Implicits.global

// Spray
import spray.http._
import spray.routing._
import MediaTypes._
import StatusCodes._

// Twitter
import com.twitter.util.Await

class RepoServiceActor extends Actor with RepoService {
  def actorRefFactory = context

  def receive = runRoute(route)
}

trait RepoService extends HttpService {
  val schemaStore = DynamoFactory.schemaStore
  val apiKeyStore = DynamoFactory.getStore("ApiKeys", "ApiKey", "Permission")
  
  val authenticator = TokenAuthenticator[String]("api-key") {
    key => util.FutureConverter.fromTwitter(apiKeyStore.get(key))
  }
  def auth: Directive1[String] = authenticate(authenticator)

  val route = rejectEmptyResponse {
    pathPrefix("[a-z.]+".r / "[a-zA-Z0-9_-]+".r / "[a-z]+".r /
    "[0-9]+-[0-9]+-[0-9]+".r) { (vendor, name, format, version) =>
      auth { permission =>
        pathEnd {
          get {
              respondWithMediaType(`application/json`) {
                complete {
                  Await.result(schemaStore.
                    get(s"${vendor}/${name}/${format}/${version}"))
                    match {
                      case Some(str) => str
                      case None => NotFound
                    }
                }
              }
          }
        } ~
        post {
          anyParam('json)(json =>
            respondWithMediaType(`text/html`) {
              complete {
                if (permission == "write") {
                  schemaStore.put((s"${vendor}/${name}/${format}/${version}",
                    Some(json)))
                  "Success"
                } else {
                  Unauthorized
                }
              }
            })
        }
      }

    }
  }
}
