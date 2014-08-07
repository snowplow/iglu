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

// Swagger
import com.wordnik.swagger.annotations._

/**
 * Service to retrieve multiple schemas in one request.
 * @constructor create a new catalog service with a schema and apiKey actors
 * @param schema a reference to a ``SchemaActor``
 * @param apiKey a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/schemas",
  description = "Operations for retrieving multiple schemas")
class CatalogService(schema: ActorRef, apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Catalog service's route
   */
  lazy val routes =
    rejectEmptyResponse {
      respondWithMediaType(`application/json`) {
        get {
          pathPrefix("[a-z.]+".r) { v =>
            auth { authPair =>
              if (v startsWith authPair._1) {
                pathPrefix("[a-zA-Z0-9_-]+".r) { n =>
                  pathPrefix("[a-z]+".r) { f =>
                    readFormatRoute(v, n, f)
                  } ~
                  readNameRoute(v, n)
                } ~
                readVendorRoute(v)
              } else {
                complete(Unauthorized, "You do not have sufficient privileges")
              }
            }
          }
        }
      }
    }

  /**
   * Catalog route to retrieve every schema belonging to a vendor.
   */
  @ApiOperation(value = "Retrieve every schema belonging to a vendor",
    notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schemas' vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "[metadata]")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404,
      message = "There are no schemas for this vendor")
  ))
  def readVendorRoute(v: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schema ? GetMetadataFromVendor(v)).mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schema ? GetSchemasFromVendor(v)).mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Catalog route to retrieve every version of every format of a schema.
   */
  @ApiOperation(value = "Retrieve every version of every format of a schema",
    notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schemas' vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schemas' name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "[metadata]")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404,
      message = "There are no schemas for this vendor, name combination")
  ))
  def readNameRoute(v: String, n: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schema ? GetMetadataFromName(v, n)).mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schema ? GetSchemasFromName(v, n)).mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Catalog route to retrieve every version of a particular format of a schema.
   */
  @ApiOperation(value = """Retrieve every version of a particular format of a
    schema""", notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schemas' vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schemas' name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "format", value = "Schemas' format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "[metadata]")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404, message =
      "There are no schemas for this vendor, name, format combination")
  ))
  def readFormatRoute(v: String, n: String, f: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schema ? GetMetadataFromFormat(v, n, f)).
            mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schema ? GetSchemasFromFormat(v, n, f)).mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Creates a ``TokenAuthenticator`` to extract the api-key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api_key") {
    key => (apiKey ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user using the authenticator.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)
}
