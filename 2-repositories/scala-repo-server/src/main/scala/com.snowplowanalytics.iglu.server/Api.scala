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
import spray.http.{
  HttpRequest,
  HttpResponse,
  HttpEntity,
  HttpCookie,
  SomeOrigins,
  AllOrigins
}
import spray.http.HttpHeaders.{
  `Remote-Address`,
  `Raw-Request-URI`,
  `Origin`,
  `Access-Control-Allow-Origin`,
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Headers`
}

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
    } ~
    options {
      requestInstance { request =>
        complete(preflightResponse(request))
      }
    }

  /**
   * Creates a response to the CORS preflight Options request
   *
   * @param request Incoming preflight Options request
   * @return Response granting permissions to make the actual request
   */
  def preflightResponse(request: HttpRequest) = HttpResponse().withHeaders(List(
    getAccesssControlAllowOriginHeader(request),
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`( "Content-Type")))

  /**
   * Creates an Access-Control-Allow-Origin header which specifically
   * allows the domain which made the request
   *
   * @param request Incoming request
   * @return Header
   */
  def getAccesssControlAllowOriginHeader(request: HttpRequest) =
    `Access-Control-Allow-Origin`(request.headers.find(_ match {
      case `Origin`(origin) => true
      case _ => false
    }) match {
      case Some(`Origin`(origin)) => SomeOrigins(origin)
      case _ => AllOrigins
    })

}
