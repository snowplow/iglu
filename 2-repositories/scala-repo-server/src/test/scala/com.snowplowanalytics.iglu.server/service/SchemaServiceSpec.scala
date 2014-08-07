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

class SchemaServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val writeKey = "a89c5f27-fe76-4754-8a07-d41884af1074"
  val faultyKey = "51ffa158-ba4b-4e27-a4ff-dfb5639b5453"
  val wrongVendorKey = "83e7c051-cd68-4e44-8b36-09182fa158d5"
  val notUuidKey = "83e7c051-cd68-4e44-8b36-09182f8d5"

  val validSchema = 
    """{
      "self": {
        "vendor": "com.snowplowanalytics.snowplow",
        "name": "ad_click",
        "format": "jsonschema",
        "version": "1-0-0"
      }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"

  val validSchemaUri = validSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22")

  val vendor = "com.snowplowanalytics.snowplow"
  val format = "jsonschema"
  val version = "1-0-0"

  val start = "/api/schemas/"
  val url = start + vendor + "/ad_click/" + format + "/" + version
  val faultyUrl = start +  vendor + "/ad_click/jsonchema/" + version
  val metaUrl = start + vendor + "/ad_click/" + format + "/" + version +
    "?filter=metadata"
  val postUrl1 = start + vendor + "/unit_test1/" + format + "/" + version
  val postUrl2 = start + vendor + "/unit_test2/" + format + "/" + version +
    "?json=" + validSchemaUri
  val postUrl3 = start + vendor + "/unit_test3/" + format + "/" + version
  val postUrl4 = start + vendor + "/unit_test4/" + format + "/" + version +
    "?json=" + validSchemaUri
  val postUrl6 = url + "?json=" + validSchemaUri
  val postUrl7 = start + vendor + "/unit_test7/" + format + "/" + version +
    "?json=" + invalidSchemaUri
  val postUrl8 = start + vendor + "/unit_test8/" + format + "/" + version +
    "?json=" + notJson

  sequential

  "SchemaService" should {

    "for GET requests" should {

      "return a proper json for well-formed GET requests" in {
        Get(url) ~> addHeader("api_key", readKey) ~> routes ~> check {
          status === OK
          responseAs[String] must contain("\"name\" : \"ad_click\"")
        }
      }

      "return proper metadata for well-formed GET requests" in {
        Get(metaUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
          status === OK
          responseAs[String] must contain(vendor) and contain("ad_click") and
            contain(format) and contain(version)
        }
      }

      "return a 404 for GET requests for which the schema is not in the db" in {
        Get(faultyUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
          status === NotFound
          responseAs[String] must
            contain("There are no schemas available here")
        }
      }

      "return a 401 if no api_key is found in the db" in {
        Get(url) ~> addHeader("api_key", faultyKey) ~> sealRoute(routes) ~>
          check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
          }
      }

      "return a 401 if the api key provided is not an uuid" in {
        Get(url) ~> addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~>
          check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
          }
      }

      "return a 401 if no api_key is provided" in {
        Get(url) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor""" in {
          Get(url) ~> addHeader("api_key", wrongVendorKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
      }

      "leave GET requests to other paths unhandled" in {
        Get("/test") ~> routes ~> check {
          handled must beFalse
        }
      }
    }

    "for POST requests" should {

      //should be removed from db before running tests for now
      "return success if the json is passed as form data" in {
        Post(postUrl1, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Schema added successfully")
          }
      }

      //should be removed from db before running tests for now
      "return success if the json is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Schema added successfully")
          }
      }

      "return a 401 if the schema already exists with form data" in {
        Post(postUrl6) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 401 if the schema already exists with query param" in {
        Post(url, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 405 if no form or query param is specified" in {
        Post(postUrl3) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === MethodNotAllowed
            responseAs[String] must contain("HTTP method not allowed")
          }
      }

      """return 401 if the api key doesn't have sufficient permissions with
        query param""" in {
          Post(postUrl4) ~> addHeader("api_key", readKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
      }

      """return a 401 if the api key doesn't have sufficient permissions
        with form data""" in {
          Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
            addHeader("api_key", readKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      "return a 401 if no api_key is specified with query param" in {
        Post(postUrl4) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      "return a 401 if no api_key is specified with form data" in {
        Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The resource requires authentication")
        }
      }

      "return a 401 if the api key is not an uuid with form data" in {
        Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
        }
      }

      "return a 401 if the api key is not an uuid with query param" in {
        Post(postUrl4) ~> addHeader("api_key", notUuidKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
        }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor with query param""" in {
          Post(postUrl6) ~> addHeader("api_key", wrongVendorKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor with form data""" in {
          Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
            addHeader("api_key", wrongVendorKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      """return a 400 if the supplied json is not self-describing with query
      param and contain a validation failure report""" in {
        Post(postUrl7) ~> addHeader("api_key", writeKey) ~> sealRoute(routes) ~>
          check {
            status === BadRequest
            responseAs[String] must
              contain("The json provided is not a valid") and
              contain("report")
          }
      }

      """"return a 400 if the supplied json is not self-describing with form
      data and contain a validation failure report""" in {
        Post(postUrl3, FormData(Seq("json" -> invalidSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must
              contain("The json provided is not a valid") and
              contain("report")
          }
      }

      "return a 400 if the supplied string is not a json with query param" in {
        Post(postUrl8) ~> addHeader("api_key", writeKey) ~> sealRoute(routes) ~>
          check {
            status === BadRequest
            responseAs[String] must contain("The json provided is not valid")
          }
      }

      "return a 400 if the supplied string is not a json with form data" in {
        Post(postUrl3, FormData(Seq("json" -> notJson))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must contain("The json provided is not valid")
          }
      }
    }
  }
}
