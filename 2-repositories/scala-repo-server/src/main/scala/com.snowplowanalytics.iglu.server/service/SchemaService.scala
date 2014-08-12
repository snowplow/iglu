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
 * Service to interact with schemas.
 * @constructor creates a new schema service with a schema and apiKey actors
 * @param schema a reference to a ``SchemaActor``
 * @param apiKey a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/schemas",
  description = "Operations dealing with individual and multiple schemas")
class SchemaService(schema: ActorRef, apiKey: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Creates a ``TokenAuthenticator`` to extract the api_key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api_key") {
    key => (apiKey ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user using the authenticator.
   */
  def auth(vendors: List[String]): Directive1[(String, String)] =
    authenticate(authenticator) flatMap { authPair =>
      if (vendors.forall(_ startsWith authPair._1)) {
        provide(authPair)
      } else {
        complete(Unauthorized, "You do not have sufficient privileges")
      }
    }


  /**
   * Directive to validate the json provided (either by query param or form
   * data) is self-describing.
   */
  def validateJson: Directive1[String] =
    anyParam('json) flatMap { json =>
      onSuccess((schema ? Validate(json)).mapTo[(StatusCode, String)]) flatMap {
        ext =>
          ext match {
            case (OK, j) => provide(j)
            case res => complete(res)
          }
      }
    }

  /**
   * Schema service's route
   */
  lazy val routes =
    rejectEmptyResponse {
      respondWithMediaType(`application/json`) {
        pathPrefix("[a-z.]+".r) { v =>
          auth(List(v)) { authPair =>
            post {
              pathPrefix("[a-zA-Z0-9_-]+".r / "[a-z]+".r /
                "[0-9]+-[0-9]+-[0-9]+".r) { (n, f, vs) =>
                  addRoute(v, n, f, vs, authPair)
                }
            }
          }
        } ~
        pathPrefix("[a-z.]+".r.repeat(separator = ",")) { v =>
          auth(v) { authPair =>
            get {
              pathPrefix("[a-zA-Z0-9_-]+".r.repeat(separator = ",")) { n =>
                pathPrefix("[a-z]+".r.repeat(separator = ",")) { f =>
                  pathPrefix(
                    "[0-9]+-[0-9]+-[0-9]+".r.repeat(separator = ",")) { vs =>
                      readRoute(v, n, f, vs)
                    } ~
                  readFormatRoute(v, n, f)
                } ~
                readNameRoute(v, n)
              } ~
              readVendorRoute(v)
            }
          }
        }
      }
    }

  /**
   * Post route
   */
  @ApiOperation(value = "Adds a new schema to the repository",
    httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "version", value = "Schema's version",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "json", value = "Schema to be added",
      required = true, dataType = "string", paramType = "query")
    ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Schema added successfully"),
    new ApiResponse(code = 401, message = "This schema already exists"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def addRoute(v: String, n: String, f: String, vs: String,
    authPair: (String, String)) =
      validateJson { json =>
        if (authPair._2 == "write") {
          complete {
            (schema ? AddSchema(v, n, f, vs, json)).
              mapTo[(StatusCode, String)]
          }
        } else {
          complete(Unauthorized, "You do not have sufficient privileges")
        }
      }

  /**
   * Route to retrieve single schemas.
   */
  @ApiOperation(value = """Retrieves a schema based on its (vendor, name,
    format, version)""", notes = "Returns a schema", httpMethod = "GET",
  response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor",
      value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "names",
      value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "formats",
      value = "Comma-separated list of schema formats",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "versions",
      value = "Comma-separated list of schema versions",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "metadata")
    ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404, message = "There are no schemas available here")
  ))
  def readRoute(v: List[String], n: List[String], f: List[String],
    vs: List[String]) =
      anyParam('filter.?) { filter =>
        filter match {
          case Some("metadata") => complete {
            (schema ? GetMetadata(v, n, f, vs)).mapTo[(StatusCode, String)]
          }
          case _ => complete {
            (schema ? GetSchema(v, n, f, vs)).mapTo[(StatusCode, String)]
          }
        }
      }

  /**
   * Route to retrieve every version of a particular format of a schema.
   */
  @ApiOperation(value = """Retrieves every version of a particular format of a
    schema""", notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor",
      value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "names",
      value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "formats",
      value = "Comma-separated list of schema formats",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "metadata")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404, message =
      "There are no schemas for this vendor, name, format combination")
  ))
  def readFormatRoute(v: List[String], n: List[String], f: List[String]) =
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
   * Route to retrieve every version of every format of a schema.
   */
  @ApiOperation(value = "Retrieves every version of every format of a schema",
    notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor",
      value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "names",
      value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path",
      allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "metadata")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404,
      message = "There are no schemas for this vendor, name combination")
  ))
  def readNameRoute(v: List[String], n: List[String]) =
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
   * Route to retrieve every schema belonging to a vendor.
   */
  @ApiOperation(value = "Retrieves every schema belonging to a vendor",
    notes = "Returns a collection of schemas", httpMethod = "GET",
    response = classOf[String])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor",
      value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "metadata")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404,
      message = "There are no schemas for this vendor")
  ))
  def readVendorRoute(v: List[String]) =
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
}
