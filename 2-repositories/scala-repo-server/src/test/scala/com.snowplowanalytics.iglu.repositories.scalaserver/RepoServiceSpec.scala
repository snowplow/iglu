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

  "RepoService" should {
    "return a proper json for GET requests to the " + url + " path" in {
      Get(url) ~> route ~> check {
        responseAs[String] must contain("\"name\": \"ad_click\"")
      }
    }

    "leave GET requests to other paths unhandled" in {
      Get("/test") ~> route ~> check {
        handled must beFalse
      }
    }

    "return MethodNotAllowed for PUT requests to the " + url + " path" in {
      Put(url) ~> sealRoute(route) ~> check {
        status === MethodNotAllowed
        responseAs[String] === "HTTP method not allowed, supported methods: GET"
      }
    }
  }
}
