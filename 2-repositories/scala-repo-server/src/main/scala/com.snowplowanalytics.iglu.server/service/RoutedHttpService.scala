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
import scala.util.control.NonFatal

// Spray
import spray.http.StatusCodes._
import spray.http.{ HttpEntity, StatusCode }
import spray.routing._
import spray.util.LoggingContext

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

  def receive = runRoute(route)(handler, RejectionHandler.Default, context,
    RoutingSettings.default, LoggingContext.fromActorRefFactory)
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
