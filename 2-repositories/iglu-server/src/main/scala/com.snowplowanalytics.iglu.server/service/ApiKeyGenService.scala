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
package service

// This project
import actor.ApiKeyActor._
import model.ApiKey

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Scala
import scala.concurrent.{Future, ExecutionContext}

// Akka Http
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{ContentNegotiator, UnacceptedResponseContentTypeRejection}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, MalformedHeaderRejection }
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected

// javax
import javax.ws.rs.Path

// Swagger
import io.swagger.annotations._


/**
  * Service to interact with API keys.
  * @constructor create a new API key generation service with an apiKey actor
  * @param apiKeyActor a reference to a ``ApiKeyActor``
  */
@Path("/api/auth")
@Api(value = "/api/auth", tags = Array("key"), authorizations = Array(new Authorization(value = "APIKeyHeader")))
@Tag(name = "key", description = "Service to interact with API keys")
class ApiKeyGenService(apiKeyActor: ActorRef)
                      (implicit executionContext: ExecutionContext)
                      extends Directives with Service {

  /**
    * Directive to authenticate a user using the authenticator.
    */
  def auth(key: String): Directive1[(String, String)] = {
    val credentialsRequest = (apiKeyActor ? GetKey(key)).mapTo[Either[String, Option[(String, String)]]].map {
      case Right(Some(t)) => Right(t)
      case Left(error) => Left(MalformedHeaderRejection("apikey", error))
      case Right(None) => Left(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.basic("Iglu Server")))
    }
    onSuccess(credentialsRequest).flatMap {
      case Right(user) => provide(user)
      case Left(rejection) => reject(rejection)
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

  /**
    * API key generation service's route
    */
  lazy val routes: Route =
    rejectEmptyResponse {
      (post | delete) {
        contentTypeNegotiator(
          headerValueByName("apikey") { apikey =>
            auth(apikey) { case (_, permission) =>
              if (permission == "super") {
                path("keygen") {
                  post {
                    keygen()
                  } ~
                    delete {
                      deleteKey()
                    }
                } ~
                  path("vendor") {
                    delete {
                      deleteKeys()
                    }
                  }
              } else {
                complete(Unauthorized, "You do not have sufficient privileges")
              }
            }
          }
        )
      }
    }

  /**
    * Route to generate a pair of read and write API keys.
    */
  @Path("/keygen")
  @ApiOperation(value = "Generates a pair of read/write API keys", notes = "Returns a pair of API keys",
    httpMethod = "POST", produces = "application/json", code = 201)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor_prefix",
      value = "Vendor prefix of the API keys", required = true,
      dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 201, message = "{read: readKey, write: writeKey}"),
    new ApiResponse(code = 401, message = "This vendor prefix is conflicting with an existing one"),
    new ApiResponse(code = 401, message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def keygen(): Route =
    (parameter('vendor_prefix) | formField('vendor_prefix) | entity(as[String])) { vendorPrefix =>
      val keyCreated: Future[(StatusCode, String)] =
        (apiKeyActor ? AddBothKey(vendorPrefix)).mapTo[(StatusCode, String)]
      onSuccess(keyCreated) { (status, performed) =>
        complete(status, HttpEntity(ContentTypes.`application/json`, performed))
      }
    }

  /**
    * Route to delete every API key having a specific vendor prefix.
    */
  @Path("/vendor")
  @ApiOperation(value = "Deletes every API key having this vendor prefix", httpMethod = "DELETE",
                response = classOf[ApiKey], produces = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor_prefix",
      value = "API keys' vendor prefix", required = true, dataType = "string",
      paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200,
      message = "API key deleted for the vendor prefix"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404, message = "Vendor prefix not found")
  ))
  def deleteKeys(): Route =
    (parameter('vendor_prefix) | formField('vendor_prefix)) { owner =>
      val keysDeleted: Future[(StatusCode, String)] =
        (apiKeyActor ? DeleteKeys(owner)).mapTo[(StatusCode, String)]
      onSuccess(keysDeleted) { (status, performed) =>
        complete(status, HttpEntity(ContentTypes.`application/json`, performed))
      }
    }

  /**
    * Route to delete a single API key.
    */
  @Path("/keygen")
  @ApiOperation(value = "Deletes a single API key", httpMethod = "DELETE",
    response = classOf[ApiKey], produces = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "key", value = "API key to be deleted",
      required = true, dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "API key successfully deleted"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 401,
      message = "The API key provided is not and UUID"),
    new ApiResponse(code = 404, message = "API key not found"),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def deleteKey(): Route =
    (parameter('key) | formField('key)) { key =>
      val keyDeleted: Future[(StatusCode, String)] =
        (apiKeyActor ? DeleteKey(key)).mapTo[(StatusCode, String)]
      onSuccess(keyDeleted) { (status, performed) =>
        complete(status, HttpEntity(ContentTypes.`application/json`, performed))
      }
    }
}
