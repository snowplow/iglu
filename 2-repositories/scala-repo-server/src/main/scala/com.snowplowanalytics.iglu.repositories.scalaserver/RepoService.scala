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

// Storehaus
import com.twitter.storehaus.dynamodb.DynamoStringStore

class RepoServiceActor(config: RepoConfig)
    extends Actor with RepoService {
  def actorRefFactory = context

  def receive = runRoute(tmpRoute)
}

trait RepoService extends HttpService {
  val store = DynamoStringStore(
    DynamoDBConfig.awsAccessKey,
    DynamoDBConfig.awsSecretKey,
    DynamoDBConfig.tableName,
    DynamoDBConfig.primaryKeyColumn,
    DynamoDBConfig.valueColumn)
  
  val tmpKey = "test"

  val tmpRoute =
    path("") {
      get {
          respondWithMediaType(`text/html`) {
            complete {
              start + store.get(tmpKey).get + end
            }
          }
      }
    }

  val start = "<html><body>"
  val end = "</html></body>"
}
