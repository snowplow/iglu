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

// Specs2 and spray testing
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest

// Spray
import spray.http._
import StatusCodes._

class RepoServiceSpec extends Specification 
    with Specs2RouteTest with RepoService {
  def actorRefFactory = system

  val url = "/com.snowplowanalytics.snowplow/ad_click/jsonschema/1-0-0"
  val faultyUrl = "/com.snowplowanalytics.snowplow/ad_click/jsonchema/1-0-0"

  "RepoService" should {
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
      Get(url) ~> addHeader("api-key", "ben") ~> route ~> check {
        status === Unauthorized
        responseAs[String] must
          contain("The supplied authentication is invalid")
      }
    }

    "return a 401 if no api-key is provided" in {
      Get(url) ~> route ~> check {
        status === Unauthorized
        responseAs[String] must contain("The resource requires authentication")
      }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/test") ~> route ~> check {
        handled must beFalse
      }
    }

    "return MethodNotAllowed for DELETE requests to the " + url + " path" in {
      Delete(url) ~> sealRoute(route) ~> check {
        status === MethodNotAllowed
        responseAs[String] === "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
