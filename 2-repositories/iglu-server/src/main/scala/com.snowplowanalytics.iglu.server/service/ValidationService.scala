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

// Akka
import akka.actor.ActorRef
import akka.pattern.ask

// Akka Http
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.{ContentNegotiator, UnacceptedResponseContentTypeRejection}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route

// javax
import javax.ws.rs.Path

//Scala
import scala.concurrent.{ExecutionContext, Future}

// Swagger
import io.swagger.annotations._


/**
  * Service to validate schemas.
  * @constructor creates a new validation service with a schema and apiKey actors
  * @param schemaActor a reference to a ``SchemaActor``
  * @param apiKeyActor a reference to a ``ApiKeyActor``
  */
@Api(value = "/api/schemas/validate", tags = Array("validation"), produces = "text/plain")
@Path("/api/schemas/validate")
class ValidationService(schemaActor: ActorRef, apiKeyActor: ActorRef)
                       (implicit executionContext: ExecutionContext)
                       extends Directives with Service {


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
    * Validation service's route
    */
  lazy val routes: Route =
    rejectEmptyResponse {
      post {
        contentTypeNegotiator(
          path(FormatPattern) { format =>
            validateSchemaRoute(format)
          } ~
          path(VendorPattern / NamePattern / FormatPattern / VersionPattern) { (v, n, f, vs) =>
            validateRoute(v, n, f, vs)
          }
        )
      }
    }

  /**
    * Route validating that the schema sent is self-describing.
    * @param schemaFormat the schema format to validate against
    */
  @Path("/{schemaFormat}")
  @ApiOperation(value = "Validates that a schema is self-describing",
    notes = "Returns a validation message", httpMethod = "POST", produces = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schema", value = "Schema to be validated",
      required = true, dataType = "string", paramType = "body")
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
  def validateSchemaRoute(@ApiParam(hidden = true) schemaFormat: String): Route =
    formField('schema) { schema =>
      val selfDescSchemaValidated: Future[(StatusCode, String)] =
        (schemaActor ? ValidateSchema(schema, schemaFormat, provideSchema = false))
          .mapTo[(StatusCode, String)]
      onSuccess(selfDescSchemaValidated) { (status, performed) =>
        complete(status, HttpEntity(ContentTypes.`application/json` , performed))
      }
    }

  /**
    * Route for validating an instance against its schema.
    * @param v schema's vendor
    * @param n schema's name
    * @param f schema's format
    * @param vs schema's version
    */
  @Path("/{vendor}/{name}/{schemaFormat}/{version}")
  @ApiOperation(value = "Validates an instance against its schema",
    notes = "Returns a validation message", httpMethod = "POST", produces = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "vendor", value = "Schema's vendor",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "name", value = "Schema's name",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "schemaFormat", value = "Schema's format",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "version", value = "Schema's version",
      required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "instance",
      value = "Instance to be validated", required = true,
      dataType = "string", paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200,
      message = "The instance is valid against the schema"),
    new ApiResponse(code = 400,
      message = "The instance provided is not valid against the schema"),
    new ApiResponse(code = 400,
      message = "The instance provided is not valid"),
    new ApiResponse(code = 404,
      message = "The schema to validate against was not found")
  ))
  def validateRoute(@ApiParam(hidden = true) v: String,
                    @ApiParam(hidden = true) n: String,
                    @ApiParam(hidden = true) f: String,
                    @ApiParam(hidden = true) vs: String): Route =
    formField('instance) { instance =>
      val schemaValidated: Future[(StatusCode, String)] =
        (schemaActor ? Validate(v, n, f, vs, instance)).mapTo[(StatusCode, String)]
      onSuccess(schemaValidated) { (status, performed) =>
        complete(status, HttpEntity(ContentTypes.`application/json` , performed))
      }
    }
}
