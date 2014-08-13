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
import actor.SchemaActor._
import actor.ApiKeyActor._
import util.TokenAuthenticator

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

//Scala
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// Spray
import spray.http.StatusCodes._
import spray.http.StatusCode
import spray.http.MediaTypes._
import spray.routing._
import spray.routing.PathMatcher.Lift

// Swagger
import com.wordnik.swagger.annotations._

/**
 * Service to validate schemas.
 * @constructor creates a new validation service with a schema and apiKey actors
 * @param schema a reference to a ``SchemaActor``
 * @param apiKey a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/schemas/validate", position = 1,
  description = "Operations dealing with schema validation")
class ValidationService(schema: ActorRef, apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Creates a ``TokenAuthenticator`` to extract the api_key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api_key") {
    key => (apiKey ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user without checking if the owner is a prefix
   * of the vendor.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  /**
   * Validation service's route
   */
  lazy val routes =
    rejectEmptyResponse {
      respondWithMediaType(`application/json`) {
        path("[a-z]+".r) { format =>
          get {
            pathEnd {
              auth { authPair =>
                validateRoute(format)
              }
            }
          }
          //pathPrefix("[a-z.-]+".r) { v =>
          //  authVendors(List(v)) { authPair =>
          //    get {
          //      path("[a-zA-Z0-9_-]+".r / "[a-z]+".r /
          //        "[0-9]+-[0-9]+-[0-9]+".r) { (n, f, vs) =>
          //          validateRoute(v, n, f, vs)
          //        }
          //    }
          //  }
          //}
        }
      }
    }

    /**
     * Route validating that the schema sent is self-describing.
     */
    @ApiOperation(value = "Validates that a schema is self-describing",
      httpMethod = "GET")
    @ApiImplicitParams(Array(
      new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
        required = true, dataType = "string", paramType = "path"),
      new ApiImplicitParam(name = "json", value = "Schema to be validated",
        required = true, dataType = "string", paramType = "query")
    ))
    @ApiResponses(Array(
      new ApiResponse(code = 200,
        message = "The schema provided is a valid self-describing schema"),
      new ApiResponse(code = 400,
        message = "The schema provided is not a valid self-describing schema"),
      new ApiResponse(code = 400, message = "The schema provided is not valid"),
      new ApiResponse(code = 400,
        message = "The schema format provided is invalid")
    ))
    def validateRoute(format: String) =
      parameter('json) { json =>
        complete {
          (schema ? Validate(json, format, false)).mapTo[(StatusCode, String)]
        }
      }
}
