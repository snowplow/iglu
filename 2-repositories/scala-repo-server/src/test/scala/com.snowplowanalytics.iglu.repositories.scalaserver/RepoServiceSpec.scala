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
package com.snowplowanalytics.iglu.repositories.scalaserver

// Scala
import scala.concurrent.duration._

// Specs2 and spray testing
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import spray.testkit.Specs2RouteTest

// Spray
import spray.http._
import StatusCodes._

class RepoServiceSpec extends Specification
    with Specs2RouteTest with RepoService with NoTimeConversions {
  def actorRefFactory = system

  // Increase test timeout
  implicit val routeTestTimeout = RouteTestTimeout(5 seconds)

  val url = "/com.snowplowanalytics.snowplow/ad_click/jsonschema/1-0-0"
  val faultyUrl = "/com.snowplowanalytics.snowplow/ad_click/jsonchema/1-0-0"
  val postUrl1 = "/com.snowplowanalytics.snowplow/unit_test1/jsonschema/1-0-0"
  val postUrl2 = "/com.snowplowanalytics.snowplow/unit_test2/jsonschema/1-0-0" +
    "?json={%20json%20}"
  val postUrl3 = faultyUrl
  val postUrl4 = "/com.snowplowanalytics.snowplow/unit_test4/jsonschema/1-0-0" +
    "?json={%20json%20}"
  val postUrl5 = "/com.snowplowanalytics.snowplow/unit_test5/jsonschema/1-0-0"
  val postUrl6 = "/com.snowplowanalytics.snowplow/unit_test6/jsonschema/1-0-0" +
    "?json={%20json%20}"
  val postUrl7 = "/com.snowplowanalytics.snowplow/unit_test7/jsonschema/1-0-0"

  "RepoService" should {
    "for GET requests" should {
      "return a proper json for GET requests to the " + url + " path" in {
        Get(url) ~> addHeader("api-key", "benRead") ~> route ~> check {
          status === OK
          responseAs[String] must contain("\"name\": \"ad_click\"")
        }
      }

      "return a 404 for GET requests for which the key is not in the db" in {
        Get(faultyUrl) ~> addHeader("api-key", "benRead") ~> route ~> check {
          status === NotFound
          responseAs[String] must
            contain("The requested resource could not be found")
        }
      }

      "return a 401 if no api-key is found" in {
        Get(url) ~> addHeader("api-key", "ben") ~> sealRoute(route) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The supplied authentication is invalid")
        }
      }

      "return a 401 if no api-key is provided" in {
        Get(url) ~> sealRoute(route) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      "leave GET requests to other paths unhandled" in {
        Get("/test") ~> route ~> check {
          handled must beFalse
        }
      }
    }

    "for POST requests" should {
      //should be removed from db before running tests for now
      "return success if the json is passed as form data" in {
        Post(postUrl1, FormData(Seq("json" -> "{ \"json\" }"))) ~>
        addHeader("api-key", "benWrite") ~> sealRoute(route) ~> check {
          status === OK
          responseAs[String] === "Success"
        }
      }

      //should be removed from db before running tests for now
      "return success if the json is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("api-key", "benWrite") ~>
        sealRoute(route) ~> check {
          status === OK
          responseAs[String] === "Success"
        }
      }

      "return a 405 if no form or query param is specified" in {
        Post(postUrl3) ~> addHeader("api-key", "benWrite") ~>
        sealRoute(route) ~> check {
          status === MethodNotAllowed
          responseAs[String] must contain("HTTP method not allowed")
        }
      }

      "return 401 if the api key doesn't have sufficient permissions " +
      "with query param" in {
        Post(postUrl4) ~> addHeader("api-key", "benRead") ~> sealRoute(route) ~>
        check {
          status === Unauthorized
          responseAs[String] must
            contain("Authentication is possible but has failed")
        }
      }

      "return a 401 if the api key doesn't have sufficient permissions " +
      "with form param" in {
        Post(postUrl5, FormData(Seq("json" -> "{ \"json\" }"))) ~>
        addHeader("api-key", "benRead") ~> sealRoute(route) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("Authentication is possible but has failed")
        }
      }

      "return a 401 if no api-key is specified with query param" in {
        Post(postUrl6) ~> sealRoute(route) ~> check {
          status === Unauthorized
          responseAs[String] must contain("The resource requires authentication")
        }
      }

      "return a 401 if no api-key is specified with form data" in {
        Post(postUrl7, FormData(Seq("json" -> "{ \"json\" }"))) ~>
        sealRoute(route) ~> check {
          status === Unauthorized
          responseAs[String] must contain("The resource requires authentication")
        }
      }
    }
  }
}
