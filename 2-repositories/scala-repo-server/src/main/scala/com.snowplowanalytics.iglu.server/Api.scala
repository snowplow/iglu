/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Origin`,
  `Origin`
}

/**
  * Api trait regroups the routes from all the different services.
  */
trait Api extends CoreActors with Core {
  val routes: Route =
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
      SwaggerDocService.routes ~
      options {
        extractRequest { request =>
          complete(preflightResponse(request))
        }
      }

  /**
    * Creates a response to the CORS preflight Options request
    *
    * @param request Incoming preflight Options request
    * @return Response granting permissions to make the actual request
    */
  def preflightResponse(request: HttpRequest) =
    HttpResponse().withHeaders(List(
      getAccesssControlAllowOriginHeader(request),
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Allow-Headers`("Content-Type")
    ))

  /**
    * Creates an Access-Control-Allow-Origin header which specifically
    * allows the domain which made the request
    *
    * @param request Incoming request
    * @return Header
    */
  def getAccesssControlAllowOriginHeader(request: HttpRequest) =
    request.headers.find( _ match {
      case Origin(_) => true
      case _ => false
    }) match {
      case Some(Origin(origins)) => `Access-Control-Allow-Origin`.forRange(HttpOriginRange.Default(origins))
      case _ => `Access-Control-Allow-Origin`.*
    }

}
