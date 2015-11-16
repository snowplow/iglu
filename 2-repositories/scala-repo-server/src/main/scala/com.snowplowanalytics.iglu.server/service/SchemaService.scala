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
import javax.ws.rs.Path

/**
 * Service to interact with schemas.
 * @constructor creates a new schema service with a schema and apiKey actors
 * @param schemaActor a reference to a ``SchemaActor``
 * @param apiKeyActor a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/schemas", position = 0,
  description = "Operations dealing with individual and multiple schemas")
class SchemaService(schemaActor: ActorRef, apiKeyActor: ActorRef)
(implicit executionContext: ExecutionContext) extends Directives with Service {

  /**
   * Creates a ``TokenAuthenticator`` to extract the api_key http header and
   * validates it against the database.
   */
  val authenticator = TokenAuthenticator[(String, String)]("api_key") {
    key => (apiKeyActor ? GetKey(key)).mapTo[Option[(String, String)]]
  }

  /**
   * Directive to authenticate a user.
   */
  def auth: Directive1[(String, String)] = authenticate(authenticator)

  /**
   * Directive to validate the schema provided (either by query param or form
   * data) is self-describing.
   */
  def validateSchema(format: String): Directive1[String] =
    anyParam('schema) | entity(as[String]) flatMap { schema =>
      onSuccess((schemaActor ? ValidateSchema(schema, format))
          .mapTo[(StatusCode, String)]) flatMap { ext =>
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
        auth { authPair =>
          post {
            addRoute(authPair._1, authPair._2)
          } ~
          put {
            updateRoute(authPair._1, authPair._2)
          } ~
          delete {
            deleteRoute(authPair._1, authPair._2)
          } ~
          get {
            path("public") {
              publicSchemasRoute(authPair._1, authPair._2)
            } ~
            path((VendorPattern / NamePattern / FormatPattern / VersionPattern)
              .repeat(separator = ",")) { list =>
                val transposed = list.map(_.toList).transpose
                readRoute(transposed(0), transposed(1), transposed(2),
                  transposed(3), authPair._1, authPair._2)
            } ~
            pathPrefix(VendorPattern.repeat(separator = ",")) { v =>
              pathPrefix(NamePattern.repeat(separator = ",")) { n =>
                pathPrefix(FormatPattern.repeat(separator = ",")) { f =>
                  path(
                    VersionPattern.repeat(separator = ",")) { vs =>
                      readRoute(v, n, f, vs, authPair._1, authPair._2)
                  } ~
                  pathEnd {
                    readFormatRoute(v, n, f, authPair._1, authPair._2)
                  }
                } ~
                pathEnd {
                  readNameRoute(v, n, authPair._1, authPair._2)
                }
              } ~
              pathEnd {
                readVendorRoute(v, authPair._1, authPair._2)
              }
            }
          }
        }
      }
    }

  /**
   * Post route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @ApiOperation(value = "Adds a new schema to the repository",
    httpMethod = "POST", consumes= "application/json", position = 0)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "version", value = "Schema's version",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Schema to be added",
      required = true, dataType = "string", paramType = "body"),
    new ApiImplicitParam(name = "isPublic",
      value = "Do you want your schema to be publicly available? Assumed false",
      required = false, defaultValue = "false", allowableValues = "true,false",
      dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 201, message = "Schema successfully added"),
    new ApiResponse(code = 400,
      message = "The schema provided is not a valid self-describing schema"),
    new ApiResponse(code = 400, message = "The schema provided is not valid"),
    new ApiResponse(code = 401, message = "This schema already exists"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def addRoute(owner: String, permission: String) =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
          anyParam('isPublic.?) { isPublic =>
            validateSchema(f) { schema =>
              complete {
                (schemaActor ? AddSchema(v, n, f, vs, schema, owner, permission,
                  isPublic == Some("true")))
                    .mapTo[(StatusCode, String)]
              }
            }
          }
        }

  /**
   * Put route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @ApiOperation(value = "Updates or creates a schema in the repository",
    httpMethod = "PUT", consumes = "application/json", position = 1)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "version", value = "Schema's version",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "body", value = "Schema to be updated",
      required = true, dataType = "string", paramType = "body"),
    new ApiImplicitParam(name = "isPublic",
      value = "Do you want your schema to be publicly available? Assumed false",
      required = false, defaultValue = "false", allowableValues = "true,false",
      dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Schema successfully updated"),
    new ApiResponse(code = 201, message = "Schema successfully added"),
    new ApiResponse(code = 400,
      message = "The schema provided is not a valid self-describing schema"),
    new ApiResponse(code = 400, message = "The schema provided is not valid"),
    new ApiResponse(code = 401,
      message = "You do not have sufficient privileges"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 500, message = "Something went wrong")
  ))
  def updateRoute(owner: String, permission: String) =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
          anyParam('isPublic.?) { isPublic =>
            validateSchema(f) { schema =>
              complete {
                (schemaActor ? UpdateSchema(v, n, f, vs, schema, owner,
                  permission, isPublic == Some("true")))
                    .mapTo[(StatusCode, String)]
              }
            }
          }
        }

  /**
   * Delete route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  def deleteRoute(owner: String, permission: String) =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
          anyParam('isPublic.?) { isPublic =>
            complete {
              (schemaActor ? DeleteSchema(v, n, f, vs, owner,
                permission, isPublic == Some("true")))
                  .mapTo[(StatusCode, String)]
            }
          }
        }

  /**
   * Route to retrieve every public schemas
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @Path(value = "/public")
  @ApiOperation(value = "Retrieves every public schema",
    notes = "Returns a collection of schemas", httpMethod = "GET", position = 2)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "filter", value = "Metadata filter",
      required = false, dataType = "string", paramType = "query",
      allowableValues = "metadata")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404,
      message = "There are no public schemas available"),
    new ApiResponse(code = 401,
      message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = """The resource requires
      authentication, which was not supplied with the request"""),
    new ApiResponse(code = 404, message = "There are no schemas available here")
  ))
  def publicSchemasRoute(owner: String, permission: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schemaActor ? GetPublicMetadata(owner, permission))
            .mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schemaActor ? GetPublicSchemas(owner, permission))
            .mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Route to retrieve single schemas.
   * @param v list of schema vendors
   * @param n list of schema names
   * @param f list of schema formats
   * @param vs list of schema versions
   * @param o the owner of the API key the request was made with
   * @param p API key's permission
   */
  @ApiOperation(value = """Retrieves a schema based on its (vendor, name,
    format, version)""", notes = "Returns a schema", httpMethod = "GET",
    position = 3)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendors",
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
    vs: List[String], o: String, p: String) =
      anyParam('filter.?) { filter =>
        filter match {
          case Some("metadata") => complete {
            (schemaActor ? GetMetadata(v, n, f, vs, o, p))
              .mapTo[(StatusCode, String)]
          }
          case _ => complete {
            (schemaActor ? GetSchema(v, n, f, vs, o, p))
              .mapTo[(StatusCode, String)]
          }
        }
      }

  /**
   * Route to retrieve every version of a particular format of a schema.
   * @param v list of schema vendors
   * @param n list of schema names
   * @param f list of schema formats
   * @param o the owner of the API key the request was made with
   * @param p API key's permission
   */
  @ApiOperation(value = """Retrieves every version of a particular format of a
    schema""", notes = "Returns a collection of schemas", httpMethod = "GET",
    position = 4)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendors",
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
  def readFormatRoute(v: List[String], n: List[String], f: List[String],
    o: String, p: String) =
      anyParam('filter.?) { filter =>
        filter match {
          case Some("metadata") => complete {
            (schemaActor ? GetMetadataFromFormat(v, n, f, o, p))
              .mapTo[(StatusCode, String)]
          }
          case _ => complete {
            (schemaActor ? GetSchemasFromFormat(v, n, f, o, p))
              .mapTo[(StatusCode, String)]
          }
        }
      }

  /**
   * Route to retrieve every version of every format of a schema.
   * @param v list of schema vendors
   * @param n list of schema names
   * @param o the owner of the API key the request was made with
   * @param p API key's permission
   */
  @ApiOperation(value = "Retrieves every version of every format of a schema",
    notes = "Returns a collection of schemas", httpMethod = "GET", position = 5)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendors",
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
  def readNameRoute(v: List[String], n: List[String], o: String, p: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schemaActor ? GetMetadataFromName(v, n, o, p))
            .mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schemaActor ? GetSchemasFromName(v, n, o, p))
            .mapTo[(StatusCode, String)]
        }
      }
    }

  /**
   * Route to retrieve every schema belonging to a vendor.
   * @param v list of schema vendors
   * @param o the owner of the API key the request was made with
   * @param p API key's permission
   */
  @ApiOperation(value = "Retrieves every schema belonging to a vendor",
    notes = "Returns a collection of schemas", httpMethod = "GET", position = 6)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendors",
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
  def readVendorRoute(v: List[String], o: String, p: String) =
    anyParam('filter.?) { filter =>
      filter match {
        case Some("metadata") => complete {
          (schemaActor ? GetMetadataFromVendor(v, o, p))
            .mapTo[(StatusCode, String)]
        }
        case _ => complete {
          (schemaActor ? GetSchemasFromVendor(v, o, p))
            .mapTo[(StatusCode, String)]
        }
      }
    }
}
