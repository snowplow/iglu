package com.snowplowanalytics.iglu.server
package service

import cats.effect.IO
import cats.implicits._

import io.circe.Json
import io.circe.literal._

import fs2.Stream

import org.http4s.{Service => _, _}
import org.http4s.circe._
import org.http4s.rho.swagger.syntax.io.createRhoMiddleware

import SpecHelpers.toBytes
import ValidationServiceSpec._

class ValidationServiceSpec extends org.specs2.Specification { def is = s2"""
  POST /validate/schema/jsonschema returns linting errors for self-describing schema $e1
  POST /validate/schema/jsonschema returns success message for valid self-describing schema $e2
  POST /validate/schema/jsonschema reports about unknown self keyword without metaschema $e3
  POST /validate/schema/jsonschema reports about non-self-describing JSON schema $e4
  POST /validate/schema/jsonschema reports malformed request for non-json body $e5
  POST /validate/schema/jsonschema reports malformed JSON Schema on unknown properties $e11

  POST /validate/instance reports invalid instance for the root of an instance $e6
  POST /validate/instance reports valid instance $e7
  POST /validate/instance returns 404 Schema not found if schema does not exist $e8
  POST /validate/instance validates an instance with private schema if apikey is appropriate $e9
  POST /validate/instance pretends a private schema does not exist if apikey is inappropriate $e10
  """

  def e1 = {
    val selfDescribingSchema = json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object"
        }"""
    val expected = json"""{
      "message":"The schema does not conform to a JSON Schema v4 specification",
      "report":[
        {"message":"The following keywords are unknown and will be ignored: [self]", "level":"WARNING", "pointer":"/"},
        {"message":"The schema is missing the \"description\" property","level":"INFO","pointer":"/"},
        {"message":"At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property","level":"WARNING","pointer":"/"}
      ]
    }"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/schema/jsonschema"))
      .withContentType(headers.`Content-Type`(MediaType.application.json))
      .withBodyStream(toBytes(selfDescribingSchema))

    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e2 = {
    val selfDescribingSchema = json"""
        {
          "$$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object",
          "description": "schema with no issues",
          "properties": { }
        }"""
    val expected = json"""{"message" : "The schema provided is a valid self-describing iglu:com.acme/nonexistent/jsonschema/1-0-0 schema"}"""

    val request = Request[IO](Method.POST, uri"/validate/schema/jsonschema")
      .withContentType(headers.`Content-Type`(MediaType.application.json))
      .withBodyStream(toBytes(selfDescribingSchema))

    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e3 = {
    val selfDescribingSchema = json"""
      {
        "self": {
          "vendor": "com.acme",
          "name": "nonexistent",
          "format": "jsonschema",
          "version": "1-0-0"
        },
        "type": "object",
        "description": "schema with no issues",
        "properties": { }
      }"""
    val expected = json"""{
      "message":"The schema does not conform to a JSON Schema v4 specification",
      "report":[
        {"message":"The following keywords are unknown and will be ignored: [self]", "level":"WARNING", "pointer":"/"}
      ]
    }"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/schema/jsonschema"))
      .withContentType(headers.`Content-Type`(MediaType.application.json))
      .withBodyStream(toBytes(selfDescribingSchema))

    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e4 = {
    val selfDescribingSchema = json"""{"type": "object", "description": "non-self-describing schema", "properties": {}}"""
    val expected = json"""{
        "message" : "The schema does not conform to a JSON Schema v4 specification",
        "report" : [{ "message" : "JSON Schema is not self-describing", "level" : "ERROR", "pointer" : "/" } ]
      }"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/schema/jsonschema"))
      .withContentType(headers.`Content-Type`(MediaType.application.json))
      .withBodyStream(toBytes(selfDescribingSchema))

    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e5 = {
    val expected = "The request body was malformed."
    val request = Request[IO](Method.POST, Uri.uri("/validate/schema/jsonschema"))
      .withBodyStream(Stream.emits("non-json".getBytes))

    val response = sendRequestGetText(request)

    response must beEqualTo(expected)
  }

  def e6 = {
    val instance =
      json"""{"schema" : "iglu:com.acme/event/jsonschema/1-0-0", "data" : [] } """
    val expected = json"""{
        "message" : "The data for a field instance is invalid against its schema",
        "report" : [
          {
            "message" : "$$: array found, object expected",
            "path" : "$$",
            "keyword" : "type",
            "targets" : ["array", "object" ]
          }
        ]
      }"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/instance"))
      .withBodyStream(toBytes(instance))
    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e7 = {
    val instance =
      json"""{"schema" : "iglu:com.acme/event/jsonschema/1-0-0", "data" : {"one": null} } """
    val expected = json"""{"message" : "Instance is valid iglu:com.acme/event/jsonschema/1-0-0"}"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/instance"))
      .withBodyStream(toBytes(instance))
    val response = sendRequest(request)

    response must beEqualTo(expected)
  }

  def e8 = {
    val instance =
      json"""{"schema" : "iglu:com.acme/does-not-exist/jsonschema/1-0-0", "data" : {} } """
    val expected =
      json"""{"message" : "The schema is not found"}"""

    val request = Request[IO](Method.POST, Uri.uri("/validate/instance"))
      .withBodyStream(toBytes(instance))

    val (responses, _) = ValidationServiceSpec.request(List(request)).unsafeRunSync()
    val response = responses.last

    val bodyExpectation = response.as[Json].unsafeRunSync() must beEqualTo(expected)
    val statusExpectation = response.status.code must beEqualTo(404)
    bodyExpectation and statusExpectation
  }

  def e9 = {
    val instance =
      json"""{"schema" : "iglu:com.acme/secret/jsonschema/1-0-0", "data" : {} } """
    val request = Request[IO](Method.POST, Uri.uri("/validate/instance"))
      .withHeaders(Headers.of(Header("apikey", SpecHelpers.readKey.toString)))
      .withBodyStream(toBytes(instance))
    val response = ValidationServiceSpec.sendRequestGetText(request)

    response must contain("$.password: is missing but it is required")
  }

  def e10 = {
    val instance =
      json"""{"schema" : "iglu:com.acme/secret/jsonschema/1-0-0", "data" : {} } """
    val expected =
      json"""{"message" : "The schema is not found"}"""
    val request = Request[IO](Method.POST, Uri.uri("/validate/instance"))
      .withHeaders(Headers.of(Header("apikey", "00000000-1111-eeee-0000-eeeeeeeeffff")))
      .withBodyStream(toBytes(instance))

    val (responses, _) = ValidationServiceSpec.request(List(request)).unsafeRunSync()
    val response = responses.last

    val bodyExpectation = response.as[Json].unsafeRunSync() must beEqualTo(expected)
    val statusExpectation = response.status.code must beEqualTo(404)
    bodyExpectation and statusExpectation
  }

  def e11 = {
    val selfDescribingSchema = json"""
        {
          "$$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object",
          "description": "schema with no issues",
          "properties": {
            "inner": {
              "description": "A property containing invalid property",
              "misplacedProperty": {"type": "string"}
            }
          }
        }"""

    val expected = json"""{
      "message" : "The schema does not conform to a JSON Schema v4 specification",
      "report" : [
        {
          "message" : "The following keywords are unknown and will be ignored: [misplacedProperty]",
          "level" : "WARNING",
          "pointer" : "/properties/inner"
        }
      ]
    }"""

    val request = Request[IO](Method.POST, uri"/validate/schema/jsonschema")
      .withContentType(headers.`Content-Type`(MediaType.application.json))
      .withBodyStream(toBytes(selfDescribingSchema))

    val response = sendRequest(request)

    response must beEqualTo(expected)
  }
}

object ValidationServiceSpec {

  def request(reqs: List[Request[IO]]) =
    SpecHelpers
      .state(storage => ValidationService.asRoutes(storage, SpecHelpers.ctx, createRhoMiddleware()))(reqs)

  def sendRequest(req: Request[IO]) =
    request(List(req))
      .flatMap { case (responses, _) => responses.last.as[Json] }
      .unsafeRunSync()

  def sendRequestGetText(req: Request[IO]) =
    request(List(req))
      .flatMap { case (responses, _) => responses.last.bodyAsText.compile.foldMonoid }
      .unsafeRunSync()
}
