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

// This project
import actor.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.MediaTypes._
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.routing._

// Swagger
import com.wordnik.swagger.annotations._

/**
 * Service to interact with API keys.
 * @constructor create a new API key generation service with an apiKey actor
 * @param apiKeyActor a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/auth/keygen", position = 2,
  description = """Operations dealing with API key generation and deletion,
  requires a super API key""")
class ApiKeyGenService(apiKeyActor: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Creates a ``TokenAuthenticator`` to extract the api_key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api_key") {
    key => (apiKeyActor ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user using the authenticator.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  /**
   * API key generation service's route
   */
  lazy val routes =
    rejectEmptyResponse {
      path("keygen") {
        respondWithMediaType(`application/json`) {
          auth { authPair =>
            if (authPair._2 == "super") {
              post {
                addRoute
              } ~
              delete {
                deleteKeysRoute ~
                deleteKeyRoute
              }
            } else {
              complete(Unauthorized, "You do not have sufficient privileges")
            }
          }
        }
      }
    }

  /**
   * Route to generate a pair of read and read and write API keys.
   */
  @ApiOperation(value = "Generates a pair of read and read/write API keys",
    notes = "Returns a pair of API keys", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor_prefix",
      value = "Vendor prefix of the API keys", required = true,
      dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "This vendor prefix is conflicting with an existing one"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def addRoute =
    (anyParam('vendor_prefix) | entity(as[String])) { vendorPrefix =>
      complete {
        (apiKeyActor ? AddBothKey(vendorPrefix)).mapTo[(StatusCode, String)]
      }
    }

  /**
   * Route to delete every API key having a specific vendor prefix.
   */
  @ApiOperation(value = "Deletes every API key having this vendor prefix",
    httpMethod = "DELETE")
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
  def deleteKeysRoute =
    anyParam('vendor_prefix) { owner =>
      complete {
        (apiKeyActor ? DeleteKeys(owner)).mapTo[(StatusCode, String)]
      }
    }

  /**
   * Route to delete a single API key.
   */
  @ApiOperation(value = "Deletes a single API key", httpMethod = "DELETE")
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
  def deleteKeyRoute =
    anyParam('key) { key =>
      complete {
        (apiKeyActor ? DeleteKey(key)).mapTo[(StatusCode, String)]
      }
    }
}
