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

  implicit val routeTestTimeout = RouteTestTimeout(10 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val writeKey = "a89c5f27-fe76-4754-8a07-d41884af1074"
  val faultyKey = "51ffa158-ba4b-4e27-a4ff-dfb5639b5453"
  val wrongVendorKey = "83e7c051-cd68-4e44-8b36-09182fa158d5"

  val url = "/com.snowplowanalytics.snowplow/ad_click/jsonschema/1-0-0"
  val faultyUrl = "/com.snowplowanalytics.snowplow/ad_click/jsonchema/1-0-0"
  val postUrl1 = "/com.snowplowanalytics.snowplow/unit_test1/jsonschema/1-0-0"
  val postUrl2 = "/com.snowplowanalytics.snowplow/unit_test2/jsonschema/1-0-0" +
    "?json={%20%22some%22:%20%22json%22%20}"
  val postUrl3 = "/com.snowplowanalytics.snowplow/unit_test3/jsonschema/1-0-0"
  val postUrl4 = "/com.snowplowanalytics.snowplow/unit_test4/jsonschema/1-0-0" +
    "?json={%20%22some%22:%20%22json%22%20}"
  val postUrl5 = "/com.snowplowanalytics.snowplow/unit_test5/jsonschema/1-0-0"
  val postUrl6 = "/com.snowplowanalytics.snowplow/unit_test6/jsonschema/1-0-0" +
    "?json={%20%22some%22:%20%22json%22%20}"
  val postUrl7 = "/com.snowplowanalytics.snowplow/unit_test7/jsonschema/1-0-0"
  val postUrl8 = url + "?json={%20%22some%22:%20%22json%22%20}"

  sequential

  "SchemaService" should {

    "for GET requests" should {

      "return a proper json for well-formed GET requests" in {
        Get(url) ~> addHeader("api-key", readKey) ~> routes ~> check {
          status === OK
          responseAs[String] must contain("\"name\": \"ad_click\"")
        }
      }

      "return a 404 for GET requests for which the schema is not in the db" in {
        Get(faultyUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
          status === NotFound
          responseAs[String] must
            contain("There are no schemas available here")
        }
      }

      "return a 401 if no api-key is found" in {
        Get(url) ~> addHeader("api-key", faultyKey) ~> sealRoute(routes) ~>
          check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
          }
      }

      "return a 401 if no api-key is provided" in {
        Get(url) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor""" in {
          Get(url) ~> addHeader("api-key", wrongVendorKey) ~>
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
        Post(postUrl1, FormData(Seq("json" -> """{ "some": "json" }"""))) ~>
          addHeader("api-key", writeKey) ~> sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Schema added successfully")
          }
      }

      //should be removed from db before running tests for now
      "return success if the json is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("api-key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Schema added successfully")
          }
      }

      "return a 401 if the schema already exists with form data" in {
        Post(postUrl8) ~> addHeader("api-key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 401 if the schema already exists with query param" in {
        Post(url, FormData(Seq("json" -> """{ "some": "json" }"""))) ~>
          addHeader("api-key", writeKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 405 if no form or query param is specified" in {
        Post(postUrl3) ~> addHeader("api-key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === MethodNotAllowed
            responseAs[String] must contain("HTTP method not allowed")
          }
      }

      """return 401 if the api key doesn't have sufficient permissions with
        query param""" in {
          Post(postUrl4) ~> addHeader("api-key", readKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
      }

      """return a 401 if the api key doesn't have sufficient permissions
        with form data""" in {
          Post(postUrl5, FormData(Seq("json" -> """{ "some": "json" }"""))) ~>
            addHeader("api-key", readKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      "return a 401 if no api-key is specified with query param" in {
        Post(postUrl6) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      "return a 401 if no api-key is specified with form data" in {
        Post(postUrl7, FormData(Seq("json" -> """{ "some": "json" }"""))) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The resource requires authentication")
        }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor with query param""" in {
          Post(postUrl6) ~> addHeader("api-key", wrongVendorKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      """return a 401 if the owner of the api key is not a prefix of the
        schema's vendor with form data""" in {
          Post(postUrl7, FormData(Seq("json" -> """{ "some": "json" }"""))) ~>
            addHeader("api-key", wrongVendorKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }
    }
  }
}
