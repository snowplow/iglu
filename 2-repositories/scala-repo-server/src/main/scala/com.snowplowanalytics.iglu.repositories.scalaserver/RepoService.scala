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

// AWS
import com.amazonaws.services.dynamodbv2.model._

// Akka and Spray
import akka.actor.Actor
import spray.http._
import spray.routing._
import MediaTypes._

class RepoServiceActor(config: RepoConfig)
    extends Actor with RepoService {
  def actorRefFactory = context

  def receive = runRoute(tmpRoute)
}

trait RepoService extends HttpService {
  val store = DynamoFactory.getStore
  
  val tmpKey = "test"

  val tmpRoute =
    path("[a-z.]+".r / "[a-zA-Z0-9_-]".r / "[a-z]+".r / "[0-9]+-[0-9]+-[0-9]+".r) {
      (vendor, name, format, version) =>
        get {
            respondWithMediaType(`application/json`) {
              complete {
                store.get(vendor + name + format + version).get match {
                  case Some(str) => str
                  case None => "{ 404 }"
                }
              }
            }
        }
    }
}
