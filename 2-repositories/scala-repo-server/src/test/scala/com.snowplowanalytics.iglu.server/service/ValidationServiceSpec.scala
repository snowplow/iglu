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

// Scala
import scala.concurrent.duration._

// Akka Http
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import akka.http.scaladsl.model.StatusCodes._

// Specs2
import org.specs2.mutable.Specification


class ValidationServiceSpec extends Specification
  with Api with Specs2RouteTest with SetupAndDestroy {

  override def afterAll() = super.afterAll()

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

  val key = "6eadba20-9b9f-4648-9c23-770272f8d627"

  val vendor = "com.snowplowanalytics.snowplow"
  val name = "ad_click"
  val format = "jsonschema"
  val invalidFormat = "jsontable"
  val version = "1-0-0"

  val validSchema =
    s"""{
      "self": {
        "vendor": "vendor",
        "name": "name",
        "format": "$format",
        "version": "1-0-0"
        }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"

  val validInstance = """{ "targetUrl": "somestr" }"""

  val validSchemaUri = validSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val validInstanceUri = validInstance.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")

  val start = "/api/schemas/validate/"

  val validUrl = s"${start}${format}?schema=${validSchemaUri}"
  val invalidUrl = s"${start}${format}?schema=${invalidSchemaUri}"
  val notSchemaUrl = s"${start}${format}?schema=${notJson}"
  val invalidFormatUrl = s"${start}${invalidFormat}?schema=${validSchemaUri}"

  val validInstanceUrl = s"${start}${vendor}/${name}/${format}/${version}" +
    s"?instance=${validInstanceUri}"
  val invalidInstanceUrl = s"${start}${vendor}/${name}/${format}/${version}" +
    s"?instance=${invalidSchemaUri}"
  val notInstanceUrl = s"${start}${vendor}/${name}/${format}/${version}" +
    s"?instance=${notJson}"
  val notFoundInstanceUrl = s"${start}${vendor}/${name}/${format}/1-0-100" +
    s"?instance=${validInstanceUri}"

  sequential

  "ValidationService" should {

    "for self-describing validation" should {

      "return a 200 if the schema provided is self-describing" in {
        Get(validUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === OK
          responseAs[String] must
            contain("The schema provided is a valid self-describing schema")
        }
      }

      "return a 400 if the schema provided is not self-describing" in {
        Get(invalidUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must contain(
            "The schema provided is not a valid self-describing schema") and
            contain("report")
        }
      }

      "return a 400 if the schema provided is not valid" in {
        Get(notSchemaUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      "return a 400 if the format provided is not supported" in {
        Get(invalidFormatUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must
            contain("The schema format provided is not supported")
        }
      }
    }

    "for instance validation" should {

      "return a 200 if the instance is valid against the schema" in {
        Get(validInstanceUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === OK
          responseAs[String] must
            contain("The instance provided is valid against the schema")
        }
      }

      "return a 400 if the instance is not valid against the schema" in {
        Get(invalidInstanceUrl) ~> addHeader("apikey", key) ~> routes ~>
        check {
          status === BadRequest
          responseAs[String] must
            contain("The instance provided is not valid against the schema")
        }
      }

      "return a 400 if the instance provided is not valid" in {
        Get(notInstanceUrl) ~> addHeader("apikey", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must contain("The instance provided is not valid")
        }
      }

      "return a 404 if the schema to validate against was not found" in {
        Get(notFoundInstanceUrl) ~> addHeader("apikey", key) ~> routes ~>
        check {
          status === NotFound
          responseAs[String] must
            contain("The schema to validate against was not found")
        }
      }
    }
  }
}
