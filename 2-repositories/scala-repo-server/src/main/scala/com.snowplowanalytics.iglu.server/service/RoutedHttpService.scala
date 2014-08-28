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

// Akka
import akka.actor.{ Actor, ActorLogging }

// Scala
import scala.reflect.runtime.universe._
import scala.util.control.NonFatal

// Spray
import spray.http.StatusCodes._
import spray.http.{ HttpEntity, StatusCode }
import spray.routing._
import spray.util.LoggingContext

// Swagger
import com.gettyimages.spray.swagger._
import com.wordnik.swagger.model.ApiInfo

/**
 * HttpService used to run the different routes coming from the
 * ``SchemaService``, ``CatalogService`` and ``ApiKeyGenService``
 */
class RoutedHttpService(route: Route) extends Actor with HttpService
with ActorLogging {
  implicit def actorRefFactory = context

  implicit val handler = ExceptionHandler {
    case NonFatal(ErrorResponseException(statusCode, entity)) => ctx =>
      ctx.complete((statusCode, entity))
    case NonFatal(e) => ctx => {
      log.error(e, InternalServerError.defaultMessage)
      ctx.complete(InternalServerError)
    }
  }

  def receive = runRoute(route ~ swaggerService.routes)(handler,
    RejectionHandler.Default, context, RoutingSettings.default,
    LoggingContext.fromActorRefFactory)

  val swaggerService = new SwaggerHttpService {
    override def apiTypes = Seq(typeOf[SchemaService], typeOf[ApiKeyGenService],
      typeOf[ValidationService])
    override def apiVersion = "0.2"
    override def baseUrl = "/"
    override def docsPath = "api-docs"
    override def actorRefFactory = context
    override def apiInfo = Some(new ApiInfo("Iglu schema repository",
      """This is the API documentation for the Iglu schema repository, a
      machine-readable schema repository built by Snowplow Analytics. Fill in
      your API key at the top of the page if you own one. Otherwise, please,
      request an API key from the administrators of this Iglu repository.""",
      "TOS url", "contact@snowplowanalytics.com", "Apache 2.0",
      "https://github.com/snowplow/iglu/blob/master/LICENSE-2.0.txt"))
  }
}

case class ErrorResponseException(responseStatus: StatusCode,
  response: Option[HttpEntity]) extends Exception

trait FailureHandling {
  this: HttpService =>

  def rejectionHandler = RejectionHandler.Default

  def exceptionHandler(implicit log: LoggingContext) = ExceptionHandler {
    case e: IllegalArgumentException => ctx =>
      loggedFailureResponse(ctx, e,
        message = "The server did not understand the request: " + e.getMessage,
        error = NotAcceptable)
    case e: NoSuchElementException => ctx =>
      loggedFailureResponse(ctx, e,
        message = "Missing information: " + e.getMessage,
        error = NotFound)
    case t: Throwable => ctx => loggedFailureResponse(ctx, t)
  }

  private def loggedFailureResponse(ctx: RequestContext, thrown: Throwable,
    message: String = "The server is having problems",
    error: StatusCode = InternalServerError)
    (implicit log: LoggingContext): Unit = {

    log.error(thrown, ctx.request.toString)
    ctx.complete((error, message))
  }
}
