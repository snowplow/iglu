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

// Java
import java.util.UUID

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http._
import StatusCodes._
import spray.json._
import DefaultJsonProtocol._
import spray.testkit.Specs2RouteTest

class ApiKeyGenServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(30 seconds)

  val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  val superKey = "d0ca1d61-f6a8-4b40-a421-dbec5b9cdbad"
  val notSuperKey = "6eadba20-9b9f-4648-9c23-770272f8d627"

  var readKey = ""
  var writeKey = ""

  val deleteUrl = "/apikeygen?key="
  val ownerUrl = "/apikeygen?owner=com.test.dont.take.this"
  val conflictingOwnerUrl = "/apikeygen?owner=com.test.dont"

  sequential

  "ApiKeyGenService" should {

    "for POST requests" should {

      "return a 401 if the key provided is not super" in {
        Post(ownerUrl) ~> addHeader("api-key", notSuperKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
      }
      
      """return a proper json with the keys if the vendor is not colliding with
        anyone""" in {
          Post(ownerUrl) ~> addHeader("api-key", superKey) ~>
            sealRoute(routes) ~> check {
              status === OK
              val response = responseAs[String]
              response must contain("read") and contain("write")
              val map = JsonParser(response).convertTo[Map[String, String]]
              readKey = map getOrElse("read", "")
              writeKey = map getOrElse("write", "")
              readKey must beMatching(uidRegex)
              writeKey must beMatching(uidRegex)
            }
        }

      "return a 401 if a new vendor is conflicting with an existing one" in {
        Post(conflictingOwnerUrl) ~> addHeader("api-key", superKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("This vendor is conflicting with an existing one")
          }
      }
    }

    "for DELETE requests" should {

      "return a 401 if the key provided is not super" in {
        Delete(deleteUrl + readKey) ~> addHeader("api-key", notSuperKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
      }

      "return a 404 if the key is not found" in {
        Delete(deleteUrl + UUID.randomUUID().toString) ~>
          addHeader("api-key", superKey) ~> sealRoute(routes) ~> check {
            status === NotFound
            responseAs[String] must contain("Api key not found")
          }
      }

      "return a 200 if the key is found and sufficient privileges" in {
        Delete(deleteUrl + readKey) ~> addHeader("api-key", superKey) ~>
          sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Api key successfully deleted")
          }
        Delete(deleteUrl + writeKey) ~> addHeader("api-key", superKey) ~>
          sealRoute(routes) ~> check {
            status === OK
            responseAs[String] must contain("Api key successfully deleted")
          }
      }
    }
  }
}
