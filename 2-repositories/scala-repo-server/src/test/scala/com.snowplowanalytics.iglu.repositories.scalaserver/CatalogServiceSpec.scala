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
package test

// This project
import api.Api

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http._
import StatusCodes._
import spray.testkit.Specs2RouteTest

class CatalogServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(5 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"

  val vendorUrl = "/com.snowplowanalytics.snowplow/"
  val nameUrl = "/com.snowplowanalytics.snowplow/ad_click/"
  val formatUrl = "/com.snowplowanalytics.snowplow/ad_click/jsonformat"
  
  "CatalogService" should {
    "for GET requests" should {
      "for vendor based url" should {
        "return the catalog of available schemas for this vendor" in {
          Get(vendorUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain("\"name\": \"ad_click\"") and
              contain("\"name\": \"ad_click2\"")
          }
        }
      }

      "for name based url" should {
        "return the catalog of available schemas for this name" in {
          Get(nameUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain("\"version\": \"1-0-0\"") and
              contain("\"version\": \"1-0-0\"")
          }
        }
      }

      "for format based url" should {
        "return the catalog of available schemas for this format" in {
          Get(formatUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain("\"version\": \"1-0-0\"") and
              contain("\"version\": \"1-0-0\"")
          }
        }
      }
    }
  }
}
