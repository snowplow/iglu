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
 * Service to interact with api keys.
 * @constructor create a new api key generation service with an apiKey actor
 * @param apiKey a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/auth/keygen",
  description = "Operations dealing with api key generation and deletion")
class ApiKeyGenService(apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Api key generation service's route
   */
  lazy val routes =
    rejectEmptyResponse {
      path("keygen") {
        respondWithMediaType(`application/json`) {
          auth { authPair =>
            if (authPair._2 == "super") {
              anyParam('owner) { owner =>
                addRoute(owner) ~
                deleteKeysRoute(owner)
              } ~
              deleteKeyRoute
            } else {
              complete(Unauthorized, "You do not have sufficient privileges")
            }
          }
        }
      }
    }

  /**
   * Route to generate a pair of read and read and write api keys.
   */
  @ApiOperation(value = "Generate a pair of read and read write api keys",
    notes = "Returns a pair of api keys", httpMethod = "POST",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "owner", value = "Future owner of the api keys",
      required = true, dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "This owner is conflicting with an existing one"),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def addRoute(owner: String) =
    post {
      complete {
        (apiKey ? AddBothKey(owner)).mapTo[(StatusCode, String)]
      }
    }

  /**
   * Route to delete every api key belonging to an owner.
   */
  @ApiOperation(value = "Delete every api key belonging to an owner",
    httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "owner", value = "Api keys' owner",
      required = true, dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Api key delete for the owner"),
    new ApiResponse(code = 404, message = "Owner not found")
  ))
  def deleteKeysRoute(owner: String) =
    delete {
      complete {
        (apiKey ? DeleteKeys(owner)).mapTo[(StatusCode, String)]
      }
    }

  /**
   * Route to delete a single api key.
   */
  @ApiOperation(value = "Delete a single api key", httpMethod = "DELETE")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "key", value = "Api key to be deleted",
      required = true, dataType = "string", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Api key successfully deleted"),
    new ApiResponse(code = 401,
      message = "The api key provided is not and UUID"),
    new ApiResponse(code = 404, message = "Api key not found"),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def deleteKeyRoute =
    anyParam('key) { key =>
      delete {
        complete {
          (apiKey ? DeleteKey(key)).mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Creates a ``TokenAuthenticator`` to extract the api-key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api-key") {
    key => (apiKey ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user using the authenticator.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)
}
