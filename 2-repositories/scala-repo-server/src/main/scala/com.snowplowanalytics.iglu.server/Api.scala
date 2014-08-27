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

// This project
import service.{ ApiKeyGenService, SchemaService, ValidationService }

// Scala
import scala.concurrent.ExecutionContext.Implicits.global

// Spray
import spray.routing.HttpService

/**
 * Api trait regroups the routes from all the different services.
 */
trait Api extends HttpService with CoreActors with Core {

  lazy val routes =
    pathPrefix("api") {
      pathPrefix("auth") {
        new ApiKeyGenService(apiKeyActor).routes
      } ~
      pathPrefix("schemas") {
        pathPrefix("validate") {
          new ValidationService(schemaActor, apiKeyActor).routes
        } ~
        new SchemaService(schemaActor, apiKeyActor).routes
      }
    } ~
    get {
      pathPrefix("") {
        pathEndOrSingleSlash {
          getFromResource("swagger-ui/index.html")
        }
      } ~
      getFromResourceDirectory("swagger-ui")
    }
}
