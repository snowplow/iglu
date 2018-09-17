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
import model.Schema

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

//Scala
import scala.concurrent.{ExecutionContext, Future}

// Akka Http
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route

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
@Api(value = "/api/schemas", tags = Array("schema"))
@Path("/api/schemas")
class SchemaService(val schemaActor: ActorRef, val apiKeyActor: ActorRef)
                   (implicit val executionContext: ExecutionContext)
                   extends Directives with Common with Service {

  /**
    * GET route for authenticated user
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  def getRoute(owner: String, permission: String): Route =
    path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { case (vendor, name, format, ver) =>
      readRoute(vendor, name, format, ver, owner, permission)
    } ~
      pathPrefix(VendorPattern) { vendor: String =>
        pathPrefix(NamePattern) { names: String =>
          pathPrefix(FormatPattern) { formats: String =>
            path(VersionPattern) { versions: String =>
              readRoute(vendor, names, formats, versions, owner, permission)
            } ~
              pathEnd {
                readFormatRoute(vendor, names, formats, owner, permission)
              }
          } ~
            pathEnd {
              readNameRoute(vendor, names, owner, permission)
            }
        } ~
          pathEnd {
            readVendorRoute(vendor, owner, permission)
          }
      } ~ schemasRoute(owner, permission)

  /**
   * Post route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = "Adds a new schema to the repository", httpMethod = "POST", code = 201,
    authorizations = Array(new Authorization(value = "APIKeyHeader")),
    produces = "application/json", consumes= "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "version", value = "Schema's version",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schema", value = "Schema to be added",
      required = true, dataType = "string", paramType = "body"),
    new ApiImplicitParam(name = "isPublic",
      value = "Do you want your schema to be publicly available? Assumed false",
      required = false, defaultValue = "false", allowableValues = "true,false",
      dataType = "boolean", paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 201, message = "The schema has been successfully added"),
    new ApiResponse(code = 400,
      message = "The schema provided is not a valid self-describing schema"),
    new ApiResponse(code = 400, message = "The provided schema is not valid"),
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
            validateSchema(f) { case (_, schema) =>
              val schemaAdded: Future[(StatusCode, String)] =
                (schemaActor ? AddSchema(v, n, f, vs, draftNumOfVersionedSchemas, schema, owner, permission, isPublic.contains("true"), isDraft = false)).mapTo[(StatusCode, String)]
              sendResponse(schemaAdded)
            }
          }
        }

  /**
   * Put route
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = "Updates or creates a schema in the repository", httpMethod = "PUT",
    authorizations = Array(new Authorization(value = "APIKeyHeader")),
    produces = "application/json", consumes = "application/json")
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
    new ApiResponse(code = 200, message = "The schema has been successfully updated"),
    new ApiResponse(code = 201, message = "The schema has been successfully added"),
    new ApiResponse(code = 400,
      message = "The provided schema is not a valid self-describing schema"),
    new ApiResponse(code = 400, message = "The provided schema is not valid"),
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
            validateSchema(f) { case (_, schema) =>
              val schemaUpdated: Future[(StatusCode, String)] =
                (schemaActor ? UpdateSchema(v, n, f, vs, draftNumOfVersionedSchemas, schema, owner, permission,
                  isPublic.contains("true"), isDraft = false)).mapTo[(StatusCode, String)]
              sendResponse(schemaUpdated)
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
          val schemaDeleted: Future[(StatusCode, String)] =
            (schemaActor ? DeleteSchema(v, n, f, vs, draftNumOfVersionedSchemas, owner, permission,
              isPublic.contains("true"), isDraft = false)).mapTo[(StatusCode, String)]
          sendResponse(schemaDeleted)
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
    httpMethod = "GET", produces = "application/json", response = classOf[Schema])
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
  def readVendorRoute(@ApiParam(hidden = true) v: String,
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadataFromVendor: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromVendor(v, o, p, isDraft = false)).mapTo[(StatusCode, String)]
        sendResponse(getMetadataFromVendor)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromVendor: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = true, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromVendor)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromVendor: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = false, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromVendor)
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
    httpMethod = "GET", produces = "application/json", response = classOf[Schema])
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
    new ApiResponse(code = 404, message = "There are no schemas for this vendor + name combination")
  ))
  def readNameRoute(@ApiParam(hidden = true) v: String,
                    @ApiParam(hidden = true) n: String,
                    @ApiParam(hidden = true) o: String,
                    @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadataFromName: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromName(v, n, o, p, isDraft = false)).mapTo[(StatusCode, String)]
        sendResponse(getMetadataFromName)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromName: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = true, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromName)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromName: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = false, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromName)
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
    notes = "Returns a collection of schemas", httpMethod = "GET",
    produces = "application/json", response = classOf[Schema])
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
    new ApiResponse(code = 404, message = "There are no schemas for this vendor + name + format combination")
  ))
  def readFormatRoute(@ApiParam(hidden = true) v: String,
                      @ApiParam(hidden = true) n: String,
                      @ApiParam(hidden = true) f: String,
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadaFromFormat: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromFormat(v, n, f, o, p, isDraft = false)).mapTo[(StatusCode, String)]
        sendResponse(getMetadaFromFormat)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromFormat: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = true, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromFormat)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromFormat: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = false, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromFormat)
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
    httpMethod = "GET", produces = "application/json", response = classOf[Schema])
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
  def readRoute(@ApiParam(hidden = true) v: String,
                @ApiParam(hidden = true) n: String,
                @ApiParam(hidden = true) f: String,
                @ApiParam(hidden = true) vs: String,
                @ApiParam(hidden = true) o: String,
                @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadata: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadata(v, n, f, vs, "", o, p, isDraft = false)).mapTo[(StatusCode, String)]
        sendResponse(getMetadata)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadata: Future[(StatusCode, String)] =
              (schemaActor ? GetSchema(v, n, f, vs, "", o, p, includeMetadata = true, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadata)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchema: Future[(StatusCode, String)] =
              (schemaActor ? GetSchema(v, n, f, vs, "", o, p, includeMetadata = false, isDraft = false)).mapTo[(StatusCode, String)]
            sendResponse(getSchema)
        }
    }

  /**
    * Route to retrieve all schemas
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  @Path(value = "/")
  @ApiOperation(value = "Retrieves all schemas that user has read permissions for", httpMethod = "GET",
    notes = "Returns a collection of schemas (either Iglu URI or full)", produces = "application/json", response = classOf[List[Schema]])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "filter", value = "Get only schema or only metadata",
      required = false, dataType = "string", paramType = "query", allowableValues = "metadata"),
    new ApiImplicitParam(name = "metadata", value = "Include/exclude metadata, choose 1 to include metadata in schemas",
      required = false, dataType = "string", paramType = "query", allowableValues = "1"),
    new ApiImplicitParam(name = "body", value = "Return full schemas with body instead of Iglu URI",
      required = false, dataType = "string", paramType = "query", allowableValues = "1")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "{...}", response = classOf[Schema]),
    new ApiResponse(code = 404, message = "There are no public schemas available"),
    new ApiResponse(code = 401, message = "The supplied authentication is invalid"),
    new ApiResponse(code = 401, message = "The resource requires authentication, which was not supplied with the request"),
    new ApiResponse(code = 404, message = "There are no schemas available here")
  ))
  def schemasRoute(@ApiParam(hidden = true) owner: String,
                   @ApiParam(hidden = true) permission: String): Route = {
    parameter('filter.?) {
      case Some("metadata") => toSchemaActor(GetAllMetadata(owner, permission, false))
      case _ => parameter('metadata.?) {
        case Some("1") => parameter("body".?) {
          case Some("1") =>
            toSchemaActor(GetAllSchemas(owner, permission, true, false, true))
          case _ =>
            toSchemaActor(GetAllSchemas(owner, permission, true, false, false))
        }
        case _ => parameter("body".?) {
          case Some("1") =>
            toSchemaActor(GetAllSchemas(owner, permission, false, false, true))
          case _ =>
            toSchemaActor(GetAllSchemas(owner, permission, false, false, false))
        }
      }
    }
  }
}
