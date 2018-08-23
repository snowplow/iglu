package com.snowplowanalytics.iglu.server
package service

// This project
import actor.SchemaActor._
import actor.ApiKeyActor._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import model.Schema

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

//Scala
import scala.concurrent.{ExecutionContext, Future}

// Akka Http
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.{ContentNegotiator, UnacceptedResponseContentTypeRejection}
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}

// Swagger
import io.swagger.annotations._

// javax
import javax.ws.rs.Path

/**
  * Service to interact with draft schemas.
  * @constructor creates a new draft schema service with a schema and apiKey actors
  * @param schemaActor a reference to a ``SchemaActor``
  * @param apiKeyActor a reference to a ``ApiKeyActor``
  */
@Api(value = "/api/draft", tags = Array("draft"))
@Path("/api/draft")
class DraftSchemaService(schemaActor: ActorRef, apiKeyActor: ActorRef) (implicit executionContext: ExecutionContext)
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
    * Draft Schema Service's route
    */
  lazy val routes: Route =
    rejectEmptyResponse {
      (get | post | put | delete) {
        contentTypeNegotiator {
          get {
            optionalHeaderValueByName("apikey") {
              case Some(apikey) =>
                auth(apikey) { case (owner, permission) =>
                  getDraftRoute(owner, permission)
                }
              case None =>
                getDraftRoute("-", "-")
            }
          } ~
            headerValueByName("apikey") { apikey =>
              auth(apikey) { case (owner, permission) =>
                post {
                  addDraftRoute(owner, permission)
                } ~
                  put {
                    updateDraftRoute(owner, permission)
                  } ~
                  delete {
                    deleteDraftRoute(owner, permission)
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
  @Path("/{vendor}/{name}/{schemaFormat}/{draftNumber}")
  @ApiOperation(value = "Adds a new draft schema to the repository", httpMethod = "POST", code = 201,
    authorizations = Array(new Authorization(value = "APIKeyHeader")),
    produces = "application/json", consumes= "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "draftNumber", value = "Schema's draftNumber",
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
  def addDraftRoute(@ApiParam(hidden = true) owner: String,
                    @ApiParam(hidden = true) permission: String): Route =
    path(VendorPattern / NamePattern / FormatPattern / DraftNumberPattern) { (v, n, f, dn) =>
      (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
        validateSchema(f) { schema =>
          val schemaAdded: Future[(StatusCode, String)] =
            (schemaActor ? AddSchema(v, n, f, versionOfDraftSchemas, dn, schema, owner, permission,
              isPublic.contains("true"), isDraft = true)).mapTo[(StatusCode, String)]
          sendResponse(schemaAdded)
        }
      }
    }

  /**
    * Put route
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  @Path("/{vendor}/{name}/{schemaFormat}/{draftNumber}")
  @ApiOperation(value = "Updates or creates a draft schema in the repository", httpMethod = "PUT",
    authorizations = Array(new Authorization(value = "APIKeyHeader")),
    produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "draftNumber", value = "Schema's draftNumber",
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
  def updateDraftRoute(@ApiParam(hidden = true) owner: String,
                       @ApiParam(hidden = true) permission: String): Route =
    path(VendorPattern / NamePattern / FormatPattern / DraftNumberPattern) { (v, n, f, dn) =>
      (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
        validateSchema(f) { schema =>
          val schemaUpdated: Future[(StatusCode, String)] =
            (schemaActor ? UpdateSchema(v, n, f, versionOfDraftSchemas, dn, schema, owner, permission,
              isPublic.contains("true"), isDraft = true)).mapTo[(StatusCode, String)]
          sendResponse(schemaUpdated)
        }
      }
    }

  /**
    * Delete route
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  def deleteDraftRoute(@ApiParam(hidden = true) owner: String,
                  @ApiParam(hidden = true) permission: String): Route =
    path(VendorPattern / NamePattern / FormatPattern / DraftNumberPattern) { (v, n, f, dn) =>
      (parameter('isPublic.?) | formField('isPublic.?)) { isPublic =>
        val schemaDeleted: Future[(StatusCode, String)] =
          (schemaActor ? DeleteSchema(v, n, f, versionOfDraftSchemas, dn, owner, permission,
            isPublic.contains("true"), isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(schemaDeleted)
      }
    }


  /**
    * GET route for authenticated user
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  def getDraftRoute(owner: String, permission: String): Route =
    path("public") {
      publicDraftSchemasRoute(owner, permission)
    } ~
      path((VendorPattern / NamePattern / FormatPattern / DraftNumberPattern).repeat(1, Int.MaxValue, ",")) { schemaKeys =>
        val List(vendors, names, formats, draftNumbers) = schemaKeys.map(k => List(k._1, k._2, k._3, k._4)).transpose
        readDraftRoute(vendors, names, formats, draftNumbers, owner, permission)
      } ~
      pathPrefix(VendorPattern.repeat(1, Int.MaxValue, ",")) { vendors: List[String] =>
        pathPrefix(NamePattern.repeat(1, Int.MaxValue, ",")) { names: List[String] =>
          pathPrefix(FormatPattern.repeat(1, Int.MaxValue, ",")) { formats: List[String] =>
            path(DraftNumberPattern.repeat(1, Int.MaxValue, ",")) { draftNumbers: List[String] =>
              readDraftRoute(vendors, names, formats, draftNumbers, owner, permission)
            } ~
              pathEnd {
                readDraftFormatRoute(vendors, names, formats, owner, permission)
              }
          } ~
            pathEnd {
              readDraftNameRoute(vendors, names, owner, permission)
            }
        } ~
          pathEnd {
            readDraftVendorRoute(vendors, owner, permission)
          }
      }

  /**
    * Route to retrieve every public draft schemas
    * @param owner the owner of the API key the request was made with
    * @param permission API key's permission
    */
  @Path(value = "/public")
  @ApiOperation(value = "Retrieves every public schema", httpMethod = "GET",
    notes = "Returns a collection of schemas", produces = "application/json", response = classOf[List[Schema]])
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
  def publicDraftSchemasRoute(@ApiParam(hidden = true) owner: String,
                              @ApiParam(hidden = true) permission: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getPublicMetadata: Future[(StatusCode, String)] =
          (schemaActor ? GetPublicMetadata(owner, permission, isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(getPublicMetadata)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getPublicSchemaWithMetadata: Future[(StatusCode, String)] =
              (schemaActor ? GetPublicSchemas(owner, permission, includeMetadata = true, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getPublicSchemaWithMetadata)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getPublicSchema: Future[(StatusCode, String)] =
              (schemaActor ? GetPublicSchemas(owner, permission, includeMetadata = false, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getPublicSchema)
        }
    }

  /**
    * Route to retrieve single schemas.
    * @param v list of schema vendors
    * @param n list of schema names
    * @param f list of schema formats
    * @param dn list of schema draft numbers
    * @param o the owner of the API key the request was made with
    * @param p API key's permission
    */
  @Path("/{vendor}/{name}/{schemaFormat}/{draftNumber}")
  @ApiOperation(value = """Retrieves a schema based on its (vendor, name, format, draftNumber)""", notes = "Returns a schema",
    httpMethod = "GET", produces = "application/json", response = classOf[Schema])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Comma-separated list of schema vendors", required = true,
      dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "name", value = "Comma-separated list of schema names",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "schemaFormat", value = "Comma-separated list of schema formats",
      required = true, dataType = "string", paramType = "path", allowMultiple = true),
    new ApiImplicitParam(name = "draftNumber", value = "Comma-separated list of schema draftNumber",
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
  def readDraftRoute(@ApiParam(hidden = true) v: List[String],
                @ApiParam(hidden = true) n: List[String],
                @ApiParam(hidden = true) f: List[String],
                @ApiParam(hidden = true) dn: List[String],
                @ApiParam(hidden = true) o: String,
                @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadata: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadata(v, n, f, List.empty[String], dn, o, p, isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(getMetadata)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadata: Future[(StatusCode, String)] =
              (schemaActor ? GetSchema(v, n, f, List.empty[String], dn, o, p, includeMetadata = true, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadata)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchema: Future[(StatusCode, String)] =
              (schemaActor ? GetSchema(v, n, f, List.empty[String], dn, o, p, includeMetadata = false, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchema)
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
    new ApiResponse(code = 404, message = "There are no schemas for this vendor, name, format combination")
  ))
  def readDraftFormatRoute(@ApiParam(hidden = true) v: List[String],
                      @ApiParam(hidden = true) n: List[String],
                      @ApiParam(hidden = true) f: List[String],
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadaFromFormat: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromFormat(v, n, f, o, p, isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(getMetadaFromFormat)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromFormat: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = true, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromFormat)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromFormat: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromFormat(v, n, f, o, p, includeMetadata = false, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromFormat)
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
    new ApiResponse(code = 404, message = "There are no schemas for this vendor, name combination")
  ))
  def readDraftNameRoute(@ApiParam(hidden = true) v: List[String],
                    @ApiParam(hidden = true) n: List[String],
                    @ApiParam(hidden = true) o: String,
                    @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadataFromName: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromName(v, n, o, p, isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(getMetadataFromName)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromName: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = true, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromName)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromName: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromName(v, n, o, p, includeMetadata = false, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromName)
        }
    }

  /**
    * Route to retrieve every schema belonging to a vendor.
    * @param v list of schema vendors
    * @param o the owner of the API key the request was made with
    * @param p API key's permission
    */
  @Path("/{vendor}")
  @ApiOperation(value = "Retrieves every draft schema belonging to a vendor", notes = "Returns a collection of schemas",
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
  def readDraftVendorRoute(@ApiParam(hidden = true) v: List[String],
                      @ApiParam(hidden = true) o: String,
                      @ApiParam(hidden = true) p: String): Route =
    (parameter('filter.?) | formField('filter.?)) {
      case Some("metadata") =>
        val getMetadataFromVendor: Future[(StatusCode, String)] =
          (schemaActor ? GetMetadataFromVendor(v, o, p, isDraft = true)).mapTo[(StatusCode, String)]
        sendResponse(getMetadataFromVendor)
      case _ =>
        (parameter('metadata.?) | formField('metadata.?)) {
          case Some("1") =>
            val getSchemaWithMetadataFromVendor: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = true, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaWithMetadataFromVendor)
          case Some(m) => throw new IllegalArgumentException(s"metadata can NOT be $m")
          case None =>
            val getSchemaFromVendor: Future[(StatusCode, String)] =
              (schemaActor ? GetSchemasFromVendor(v, o, p, includeMetadata = false, isDraft = true)).mapTo[(StatusCode, String)]
            sendResponse(getSchemaFromVendor)
        }
    }

  private def sendResponse(action: Future[(StatusCode, String)]): Route = {
    val future = onSuccess(action) { (status, performed) =>
      complete(status, HttpEntity(ContentTypes.`application/json` , performed))
    }
    future
  }
}
