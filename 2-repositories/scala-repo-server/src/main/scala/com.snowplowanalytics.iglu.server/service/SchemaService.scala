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
import actor.SchemaActor._
import actor.ApiKeyActor._
import model.Schema

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

//Scala
import scala.concurrent.ExecutionContext

// Akka Http
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}
import akka.http.scaladsl.settings.RoutingSettings

// Swagger
import io.swagger.annotations._

// javax
import javax.ws.rs.Path

/**
 * Service to interact with schemas.
 * @constructor creates a new schema service with a schema and apiKey actors
 * @param schemaActor a reference to a ``SchemaActor``
 * @param apiKeyActor a reference to a ``ApiKeyActor``
 */
@Api(value = "/api/schemas", tags = Array("schema"), produces = "text/plain")
@Path("/api/schemas")
class SchemaService(schemaActor: ActorRef, apiKeyActor: ActorRef)
                   (implicit executionContext: ExecutionContext, routingSettings: RoutingSettings)
                   extends Directives with Service {

  /**
   * Directive to authenticate a user.
   */
  def auth(key: String): Directive1[(String, String)] = {
    val credentialsRequest = (apiKeyActor ? GetKey(key)).mapTo[Option[(String, String)]].map {
      case Some(t) => Right(t)
      case None => Left(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.basic("Iglu Server")))
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
  def validateSchema(format: String): Directive1[String] =
    formField('schema) | parameter('schema) | entity(as[String]) flatMap { schema =>
      onSuccess((schemaActor ? ValidateSchema(schema, format))
          .mapTo[(StatusCode, String)]) tflatMap  {
              case (OK, j) => provide(j)
              case rej => complete(rej)
            }
      }

  /**
   * Schema service's route
   */
  lazy val routes: Route =
    rejectEmptyResponse {
      (get | post | put | delete) {
        headerValueByName("apikey") { apikey =>
          auth(apikey) { case (owner, permission) =>
            post {
              addRoute(owner, permission)
            } ~
            put {
              updateRoute(owner, permission)
            } ~
            delete {
              deleteRoute(owner, permission)
            } ~
            get {
              path("public") {
                publicSchemasRoute(owner, permission)
              } ~
              path((VendorPattern / NamePattern / FormatPattern / VersionPattern).repeat(1, Int.MaxValue, ",")) { schemaKeys =>
                val List(vendors, names, formats, versions) = schemaKeys.map(k => List(k._1, k._2, k._3, k._4)).transpose
                readRoute(vendors, names, formats, versions, owner, permission)
              } ~
              pathPrefix(VendorPattern.repeat(1, Int.MaxValue, ",")) { vendors: List[String] =>
                pathPrefix(NamePattern.repeat(1, Int.MaxValue, ",")) { names: List[String] =>
                  pathPrefix(FormatPattern.repeat(1, Int.MaxValue, ",")) { formats: List[String] =>
                    path(VersionPattern.repeat(1, Int.MaxValue, ",")) { versions: List[String] =>
                      readRoute(vendors, names, formats, versions, owner, permission)
                    } ~
                    pathEnd {
                      readFormatRoute(vendors, names, formats, owner, permission)
                    }
                  } ~
                  pathEnd {
                    readNameRoute(vendors, names, owner, permission)
                  }
                } ~
                pathEnd {
                  readVendorRoute(vendors, owner, permission)
                }
              }
            }
          }
        } ~
        get {
          path("public") {
            publicSchemasRoute("-", "-")
          }
        }
      }
    }


  /**
   * Post route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = "Adds a new schema to the repository", httpMethod = "POST", code = 201,
    authorizations = Array(new Authorization(value = "APIKeyHeader")), consumes= "application/json")
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
    new ApiResponse(code = 200, message = "Placeholder response, look at 201"),
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
  def addRoute(@ApiParam(hidden = true) owner: String,
               @ApiParam(hidden = true) permission: String): Route =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
        (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
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
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = "Updates or creates a schema in the repository",
    authorizations = Array(new Authorization(value = "APIKeyHeader")), httpMethod = "PUT", consumes = "application/json")
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
  def updateRoute(@ApiParam(hidden = true) owner: String,
                  @ApiParam(hidden = true) permission: String): Route =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
        (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
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
  def deleteRoute(@ApiParam(hidden = true) owner: String,
                  @ApiParam(hidden = true) permission: String): Route =
      path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
        (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
            complete {
              (schemaActor ? DeleteSchema(v, n, f, vs, owner,
                permission, isPublic == Some("true")))
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
  @Path("/{vendor}")
  @ApiOperation(value = "Retrieves every schema belonging to a vendor", notes = "Returns a collection of schemas",
    authorizations = Array(new Authorization(value = "APIKeyHeader")), httpMethod = "GET", response = classOf[Schema])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication," +
                                          "which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas for this vendor")
  ))
  def readVendorRoute(@ApiParam(hidden = true) v: List[String],
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") => complete {
        (schemaActor ? GetMetadataFromVendor(v, o, p)).mapTo[(StatusCode, String)]
      }
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") => complete {
            (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = true)).mapTo[(StatusCode, String)]
          }
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None => complete {
            (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = false)).mapTo[(StatusCode, String)]
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
  @Path("/{vendor}/{name}")
  @ApiOperation(value = "Retrieves every version of every format of a schema", notes = "Returns a collection of schemas",
    authorizations = Array(new Authorization(value = "APIKeyHeader")), httpMethod = "GET", response = classOf[Schema])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "name", value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication," +
                                          "which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas for this vendor, name combination")
  ))
  def readNameRoute(@ApiParam(hidden = true) v: List[String],
                    @ApiParam(hidden = true) n: List[String],
                    @ApiParam(hidden = true) o: String,
                    @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") => complete {
        (schemaActor ? GetMetadataFromName(v, n, o, p)).mapTo[(StatusCode, String)]
      }
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") => complete {
            (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = true)).mapTo[(StatusCode, String)]
          }
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None => complete {
            (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = false)).mapTo[(StatusCode, String)]
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
  @Path("/{vendor}/{name}/{schemaFormat}")
  @ApiOperation(value = """Retrieves every version of a particular format of a schema""",
    authorizations = Array(new Authorization(value = "APIKeyHeader")),
    notes = "Returns a collection of schemas", httpMethod = "GET", response = classOf[Schema])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Comma-separated list of schema vendors",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "name", value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "schemaFormat", value = "Comma-separated list of schema formats",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication," +
                                          "which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas for this vendor, name, format combination")
  ))
  def readFormatRoute(@ApiParam(hidden = true) v: List[String],
                      @ApiParam(hidden = true) n: List[String],
                      @ApiParam(hidden = true) f: List[String],
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") => complete {
        (schemaActor ? GetMetadataFromFormat(v, n, f, o, p)).mapTo[(StatusCode, String)]
      }
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") => complete {
            (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = true)).mapTo[(StatusCode, String)]
          }
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None => complete {
            (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = false)).mapTo[(StatusCode, String)]
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
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = """Retrieves a schema based on its (vendor, name, format, version)""", notes = "Returns a schema",
    authorizations = Array(new Authorization(value = "APIKeyHeader")), httpMethod = "GET", response = classOf[Schema])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Comma-separated list of schema vendors", required = true,
      dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "name", value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "schemaFormat", value = "Comma-separated list of schema formats",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "version", value = "Comma-separated list of schema versions",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}"),
    new ApiResponse(code = 400, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication," +
                                          "which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas available here")
  ))
  def readRoute(@ApiParam(hidden = true) v: List[String],
                @ApiParam(hidden = true) n: List[String],
                @ApiParam(hidden = true) f: List[String],
                @ApiParam(hidden = true) vs: List[String],
                @ApiParam(hidden = true) o: String,
                @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") => complete {
        (schemaActor ? GetMetadata(v, n, f, vs, o, p)).mapTo[(StatusCode, String)]
      }
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") => complete {
            (schemaActor ? GetSchema(v, n, f, vs, o, p, includeMetadata = true)).mapTo[(StatusCode, String)]
          }
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None => complete {
            (schemaActor ? GetSchema(v, n, f, vs, o, p, includeMetadata = false)).mapTo[(StatusCode, String)]
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
    notes = "Returns a collection of schemas", httpMethod = "GET", response = classOf[List[Schema]])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}", response = classOf[Schema]),
    new ApiResponse(code = 404, message = "There are no public schemas available"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication," +
                                          "which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas available here")
  ))
  def publicSchemasRoute(@ApiParam(hidden = true) owner: String,
                         @ApiParam(hidden = true) permission: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") => complete {
        (schemaActor ? GetPublicMetadata(owner, permission))
          .mapTo[(StatusCode, String)]
      }
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") => complete {
            (schemaActor ? GetPublicSchemas(owner, permission, includeMetadata = true))
              .mapTo[(StatusCode, String)]
          }
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None => complete {
            (schemaActor ? GetPublicSchemas(owner, permission, includeMetadata = false))
              .mapTo[(StatusCode, String)]
          }
        }
    }
}
