package com.snowplowanalytics.iglu.server
package service

import akka.http.scaladsl.server.MalformedQueryParamRejection
import cats.data.Validated

// json4s
import org.json4s.jackson.JsonMethods.compact

// Scala
import scala.concurrent.{ExecutionContext, Future}

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Akka Http
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{ContentNegotiator, UnacceptedResponseContentTypeRejection}
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.{ Directive1, Directives }
import akka.http.scaladsl.server.MalformedHeaderRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}

import com.snowplowanalytics.iglu.server.model.SchemaDAO.{ validateJsonSchema, reportToJson }
import com.snowplowanalytics.iglu.server.actor.ApiKeyActor._

/** Common functionality for available schema services */
trait Common extends Directives { self: Service =>

  implicit def executionContext: ExecutionContext

  def schemaActor: ActorRef
  def apiKeyActor: ActorRef

  def addRoute(owner: String, permission: String): Route
  def getRoute(owner: String, permission: String): Route
  def updateRoute(owner: String, permission: String): Route
  def deleteRoute(owner: String, permission: String): Route

  lazy final val routes: Route =
    rejectEmptyResponse {
      (get | post | put | delete) {
        contentTypeNegotiator {
          headerValueByName('apikey) { apikey =>
            auth(apikey) { case (owner, permission) =>
              get {
                getRoute(owner, permission)
              } ~
              post {
                addRoute(owner, permission)
              } ~
              put {
                updateRoute(owner, permission)
              } ~
              delete {
                deleteRoute(owner, permission)
              }
            }
          } ~ getRoute("-", "-")
        }
      }
    }

  /**
    * Directive to authenticate a user.
    */
  def auth(key: String): Directive1[(String, String)] = {
    val credentialsRequest = (apiKeyActor ? GetKey(key)).mapTo[Either[String, Option[(String, String)]]].map {
      case Right(Some(t)) => Right(t)
      case Left(e) => Left(MalformedQueryParamRejection("apikey", e))
      case Right(None) => Left(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.basic("Iglu Server")))
    }
    onSuccess(credentialsRequest).flatMap {
      case Right(user) => provide(user)
      case Left(rejection) => reject(rejection)
    }
  }


  /**
    * Directive to validate the schema provided (either by query param or form
    * data) is self-describing.
    */
  def validateSchema(format: String): Directive1[(String, String)] =
    formField('schema) | parameter('schema) | entity(as[String]) flatMap { schema =>
      validateJsonSchema(schema) match {
        case Left(error) =>
          complete((BadRequest, error))
        case Right((_, Validated.Invalid(report))) =>
          complete((BadRequest, compact(reportToJson(report))))
        case Right((_, Validated.Valid(_))) =>
          provide(("The schema provided is a valid self-describing schema", schema))
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

  def toSchemaActor(message: AnyRef): Route = {
    sendResponse((schemaActor ? message).mapTo[(StatusCode, String)])
  }
}
