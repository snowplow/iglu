package com.snowplowanalytics.iglu.server
package service

import cats.data.Validated

// json4s
import org.json4s.jackson.JsonMethods.compact

// This project
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}

//Scala
import scala.concurrent.Future

// Akka Http
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.{ContentNegotiator, UnacceptedResponseContentTypeRejection}
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{ Directive1, Directives, Route }

import com.snowplowanalytics.iglu.server.model.SchemaDAO.{ validateJsonSchema, reportToJson }

trait SchemaLinting extends Directives {
  /**
    * Directive to validate the schema provided (either by query param or form
    * data) is self-describing.
    */
  def validateSchema(format: String): Directive1[String] =
    formField('schema) | parameter('schema) | entity(as[String]) flatMap { schema =>
      validateJsonSchema(schema) match {
        case Left(error) =>
          complete((BadRequest, error))
        case Right((_, Validated.Invalid(report))) =>
          complete((BadRequest, compact(reportToJson(report))))
        case Right((_, Validated.Valid(_))) =>
          provide("The schema provided is a valid self-describing schema")
      }
    }

  /**
    * Negotiate Content-Type header
    */
  def contentTypeNegotiator(routes: Route): Route = {
    optionalHeaderValueByType[Accept]() {
      case Some(x) =>
        if (x.acceptsAll() || x.mediaRanges.exists(_.matches(`application/json`))) routes
        else reject(UnacceptedResponseContentTypeRejection(Set(ContentNegotiator.Alternative(`application/json`))))
      case None => routes
    }
  }

  def sendResponse(action: Future[(StatusCode, String)]): Route = {
    val future = onSuccess(action) { (status, performed) =>
      complete(status, HttpEntity(ContentTypes.`application/json` , performed))
    }
    future
  }
}
