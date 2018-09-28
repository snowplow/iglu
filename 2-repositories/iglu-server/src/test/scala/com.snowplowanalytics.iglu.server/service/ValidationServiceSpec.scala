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

// Scala
import scala.concurrent.duration._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Akka
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._

// json4s
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.parse

// This project
import com.snowplowanalytics.iglu.server.actor.{ApiKeyActor, SchemaActor}

// Akka Http
// Specs2
import org.specs2.mutable.Specification


class ValidationServiceSpec extends Specification
  with Api with Specs2RouteTest with SetupAndDestroy {

  override def afterAll() = super.afterAll()
  val schemaActor: ActorRef = system.actorOf(Props(classOf[SchemaActor], config), "schemaActor3")
  val apiKeyActor: ActorRef = system.actorOf(Props(classOf[ApiKeyActor], config), "apiKeyActor3")

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

  val key = "6eadba20-9b9f-4648-9c23-770272f8d627"

  val vendor = "com.snowplowanalytics.snowplow"
  val name = "ad_click"
  val format = "jsonschema"
  val invalidFormat = "jsontable"
  val version = "1-0-0"

  val validSchema =
    """{
      "$schema" : "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",""" + s"""
      "self": {
        "vendor": "vendor",
        "name": "name",
        "format": "$format",
        "version": "1-0-0"
        },
      "properties": {
        "a": {}
      }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"
  val validSchemaWithIssues =
    """{
      "$schema" : "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",""" + s"""
      "self": {
        "vendor": "vendor",
        "name": "name",
        "format": "$format",
        "version": "1-0-0"
        },
      "properties": {
        "invalidPrimitive": {
          "minLength": -1,
          "type": "integer"
        },
        "nested": {
          "type": "array",
          "items": [{"minimum": 1, "maximum": 0}]
        }
      }
    }"""

  val validInstance = """{ "targetUrl": "somestr" }"""

  val validSchemaUri = URLEncoder.encode(validSchema, StandardCharsets.UTF_8.toString).toLowerCase
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val validInstanceUri = validInstance.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")

  val start = "/api/schemas/validate/"

  val validUrl = s"${start}${format}"
  val invalidUrl = s"${start}${format}"
  val notSchemaUrl = s"${start}${format}"
  val invalidFormatUrl = s"${start}${invalidFormat}"

  val validInstanceUrl = s"$start$vendor/$name/$format/$version"
  val invalidInstanceUrl = s"${start}${vendor}/${name}/${format}/${version}"
  val notInstanceUrl = s"${start}${vendor}/${name}/${format}/${version}"
  val notFoundInstanceUrl = s"${start}${vendor}/${name}/${format}/1-0-100"

  sequential

  "ValidationService" should {

    "for self-describing validation" should {

      "return a 200 if the schema provided is self-describing" in {
        Post(validUrl, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === OK
          contentType === `application/json`
        }
      }

      "return a 200 and errors if the schema provided is not self-describing" in {
        Post(invalidUrl, FormData(Map("schema" -> HttpEntity(`application/json`, invalidSchema)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === OK
          contentType === `application/json`
          responseAs[String] must contain("Schema is not self-describing")
        }
      }

      "return a 400 if the schema provided is not valid" in {
        Post(notSchemaUrl, FormData(Map("schema" -> HttpEntity(`application/json`, notJson)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          contentType === `application/json`
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      "return a 400 if the format provided is not supported" in {
        Post(invalidFormatUrl, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          contentType === `application/json`
          responseAs[String] must
            contain("The schema format provided is not supported")
        }
      }

      "return a 200 and list of issues if schema has any" in {
        val expected = parse(""" [
          { "message" : "Integer value must be strictly positive, in [minLength]", "level" : "ERROR", "pointer" : "/properties/invalidPrimitive" },
          { "message" : "A field with numeric type has a minimum value [1] greater than the maximum value [0]", "level" : "ERROR", "pointer" : "/properties/nested/items/0" },
          { "message" : "The schema is missing the \"description\" property", "level" : "INFO", "pointer" : "/properties/nested/items/0" },
          { "message" : "The schema is missing the \"description\" property", "level" : "INFO", "pointer" : "/properties/nested" },
          { "message" : "The schema is missing the \"description\" property", "level" : "INFO", "pointer" : "/properties/invalidPrimitive" },
          { "message" : "A numeric property should have \"minimum\" and \"maximum\" properties", "level" : "WARNING", "pointer" : "/properties/invalidPrimitive" },
          { "message" : "The schema is missing the \"description\" property", "level" : "INFO", "pointer" : "/" },
          { "message" : "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property", "level" : "WARNING", "pointer" : "/" }
        ]""")
        Post(validUrl, FormData(Map("schema" -> HttpEntity(`application/json`, validSchemaWithIssues)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === OK
          contentType === `application/json`
          (parse(responseAs[String]) \ "report") === expected
        }
      }
    }

    "for instance validation" should {

      "return a 200 if the instance is valid against the schema" in {
        Post(validInstanceUrl, FormData(Map("instance" -> HttpEntity(`application/json`, validInstance)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === OK
          contentType === `application/json`
          responseAs[String] must
            contain("The instance provided is valid against the schema")
        }
      }

      "return a 400 if the instance is not valid against the schema" in {
        Post(invalidInstanceUrl, FormData(Map("instance" -> HttpEntity(`application/json`, invalidSchema)))) ~>
          addHeader("apikey", key) ~> routes ~>
        check {
          status === BadRequest
          contentType === `application/json`
          responseAs[String] must
            contain("The instance provided is not valid against the schema")
        }
      }

      "return a 400 if the instance provided is not valid" in {
        Post(notInstanceUrl, FormData(Map("instance" -> HttpEntity(`application/json`, notJson)))) ~>
          addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          contentType === `application/json`
          responseAs[String] must contain("The instance provided is not valid")
        }
      }

      "return a 404 if the schema to validate against was not found" in {
        Post(notFoundInstanceUrl, FormData(Map("instance" -> HttpEntity(`application/json`, validInstance)))) ~>
          addHeader("apikey", key) ~> routes ~>
        check {
          status === NotFound
          contentType === `application/json`
          responseAs[String] must
            contain("The schema to validate against was not found")
        }
      }
    }
  }
}
