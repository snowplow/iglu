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
import service.{ApiKeyGenService, SchemaService, ValidationService}

// Scala
import scala.concurrent.ExecutionContext.Implicits.global

// Akka Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Origin`
}

/**
  * Api trait regroups the routes from all the different services.
  */
trait Api extends CoreActors with Core {
  val routes: Route =
    corsHandler(
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
        pathPrefix("") {
          pathEndOrSingleSlash {
            get {
              getFromResource("swagger-ui-dist/index.html")
            }
          } ~
            getFromResourceDirectory("swagger-ui-dist")
        } ~
        SwaggerDocService.routes
    )


  /** Add CORS support by setting required HTTP headers and handling prelight OPTIONS requests
    *
    * @param routes Actual Iglu routes
    * @return A response with CORS headers added
    */
  def corsHandler(routes: Route): Route =
    respondWithHeaders(List(
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Content-Type", "apikey")
    )) {
      options {
        complete(HttpResponse().withHeaders(`Access-Control-Allow-Methods`(List(GET, POST, PUT, OPTIONS, DELETE))))
      }  ~ routes
    }

}
