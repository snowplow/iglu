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

  implicit val routeTestTimeout = RouteTestTimeout(10 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val otherKey = "83e7c051-cd68-4e44-8b36-09182fa158d5"

  val vendorUrl = "/com.snowplowanalytics.snowplow"
  val nameUrl = "/com.snowplowanalytics.snowplow/ad_click"
  val formatUrl = "/com.snowplowanalytics.snowplow/ad_click/jsonschema"
  val otherVendorUrl = "/com.benfradet.project"
  val otherNameUrl = "/com.benfradet.project/ad_click"
  val otherFormatUrl = "/com.benfradet.project/ad_click/jsonschema"

  sequential
  
  "CatalogService" should {

    "for GET requests" should {

      "for vendor based url" should {

        "return the catalog of available schemas for this vendor" in {
          Get(vendorUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must
              contain("\"name\": \"ad_click\"") and
              contain("\"name\": \"ad_click2\"")
          }
        }

        "return a 404 for a vendor which has no schemas" in {
          Get(otherVendorUrl) ~> addHeader("api-key", otherKey) ~> routes ~>
            check {
              status === NotFound
              responseAs[String] must
                contain("There are no schemas for this vendor")
            }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(formatUrl) ~> addHeader("api-key", otherKey) ~> routes ~> check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for name based url" should {

        "return the catalog of available schemas for this name" in {
          Get(nameUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must
              contain("\"version\": \"1-0-0\"") and
              contain("\"version\": \"1-0-1\"")
          }
        }

        "return a 404 for a vendor/name combination which has no schemas" in {
          Get(otherNameUrl) ~> addHeader("api-key", otherKey) ~> routes ~>
            check {
              status === NotFound
              responseAs[String] must
                contain("There are no schemas for this vendor, name combinatio")
            }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(nameUrl) ~> addHeader("api-key", otherKey) ~> routes ~> check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for format based url" should {

        "return the catalog of available schemas for this format" in {
          Get(formatUrl) ~> addHeader("api-key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must
              contain("\"version\": \"1-0-0\"") and
              contain("\"version\": \"1-0-1\"")
          }
        }

        """return a 404 for a vendor/name/format combination which has
          no schemas""" in {
            Get(otherFormatUrl) ~> addHeader("api-key", otherKey) ~> routes ~>
              check {
                status === NotFound
                responseAs[String] must
                  contain("There are no schemas for this vendor, name, format ")
              }
          }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(vendorUrl) ~> addHeader("api-key", otherKey) ~> routes ~> check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }
    }
  }
}
