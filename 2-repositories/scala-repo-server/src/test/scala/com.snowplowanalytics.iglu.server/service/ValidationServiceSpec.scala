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
package test.service

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http._
import StatusCodes._
import spray.testkit.Specs2RouteTest

class ValidationServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

  val key = "6eadba20-9b9f-4648-9c23-770272f8d627"

  val format = "jsonschema"
  val invalidFormat = "jsontable"

  val validSchema =
    s"""{
      "self": {
        "vendor": "vendor",
        "name": "name",
        "format": "${format}",
        "version": "1-0-0"
        }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"

  val validSchemaUri = validSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")

  val start = "/api/schemas/validate/"

  val validUrl = s"${start}${format}?json=${validSchemaUri}"
  val invalidUrl = s"${start}${format}?json=${invalidSchemaUri}"
  val notSchemaUrl = s"${start}${format}?json=${notJson}"
  val invalidFormatUrl = s"${start}${invalidFormat}?json=${validSchemaUri}"

  sequential

  "ValidationService" should {

    "for self-describing validation" should {

      "return a 200 if the schema provided is self-describing" in {
        Get(validUrl) ~> addHeader("api_key", key) ~> routes ~> check {
          status === OK
          responseAs[String] must
            contain("The schema provided is a valid self-describing schema")
        }
      }

      "return a 400 if the schema provided is not self-describing" in {
        Get(invalidUrl) ~> addHeader("api_key", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must contain(
            "The schema provided is not a valid self-describing schema") and
            contain("report")
        }
      }

      "return a 400 if the schema provided is not valid" in {
        Get(notSchemaUrl) ~> addHeader("api_key", key) ~> routes ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      "return a 400 if the format provided is not supported" in {
        Get(invalidFormatUrl) ~> addHeader("api_key", key) ~> routes ~>
        check {
          status === BadRequest
          responseAs[String] must
            contain("The schema format provided is not supported")
        }
      }
    }
  }
}
