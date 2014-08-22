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

// Java
import java.util.UUID

// Json4s
import org.json4s._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http._
import StatusCodes._
import MediaTypes._
import spray.testkit.Specs2RouteTest

class ApiKeyGenServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(30 seconds)

  implicit val formats = DefaultFormats

  val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  val superKey = "d0ca1d61-f6a8-4b40-a421-dbec5b9cdbad"
  val notSuperKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val notUuidKey = "6ead20-9b9f-4648-9c23-770272f8d627"

  var readKey = ""
  var writeKey = ""

  val start = "/api/auth/"
  val deleteUrl = s"${start}keygen?key="

  val owner = "com.test.dont.take.this"
  val faultyOwner = "com.test.dont"
  val owner2 = "com.unittest"
  val faultyOwner2 = "com.unit"
  val owner3 = "com.no.idea"
  val faultyOwner3 = "com.no"

  //postUrl
  val postUrl1 = s"${start}keygen?owner=${owner}"
  val postUrl2 = s"${start}keygen"
  val conflictingPostUrl1 = s"${start}keygen?owner=${faultyOwner}"

  sequential

  "ApiKeyGenService" should {

    "for POST requests" should {

      "return a 401 if the key provided is not super with query param" in {
        Post(postUrl1) ~> addHeader("api_key", notSuperKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not super with form data" in {
        Post(postUrl2, FormData(Seq("owner" -> owner2))) ~>
        addHeader("api_key", notSuperKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not super with body request" in {
        Post(postUrl2, HttpEntity(`application/json`, owner3)) ~>
        addHeader("api_key", notSuperKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not an uuid with query param" in {
        Post(postUrl1) ~> addHeader("api_key", notUuidKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The supplied authentication is invalid")
        }
      }

      "return a 401 if the key provided is not an uuid with form data" in {
        Post(postUrl2, FormData(Seq("owner" -> owner2))) ~>
        addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The supplied authentication is invalid")
        }
      }

      "return a 401 if the key provided is not an uuid with body request" in {
        Post(postUrl2, HttpEntity(`application/json`, owner3)) ~>
        addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The supplied authentication is invalid")
        }
      }
      
      """return a 200 with the keys if the owner is not colliding with anyone
      with query param""" in {
        Post(postUrl1) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === Created
          val response = responseAs[String]
          response must contain("read") and contain("write")
          val map = parse(response).extract[Map[String, String]]
          readKey = map getOrElse("read", "")
          writeKey = map getOrElse("write", "")
          readKey must beMatching(uidRegex)
          writeKey must beMatching(uidRegex)
        }
      }

      //to manually delete
      """return a 200 with the keys if the owner is not colliding with anyone
      with form data""" in {
        Post(postUrl2, FormData(Seq("owner" -> owner2))) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("read") and contain("write")
        }
      }

      //to manually delete
      """return a 200 with the keys if the owner is not colliding with anyone
      with body request""" in {
        Post(postUrl2, HttpEntity(`application/json`, owner3)) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("read") and contain("write")
        }
      }

      "return a 401 if the owner already exists with quer param" in {
        Post(postUrl1) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }

      "return a 401 if the owner already exists with form data" in {
        Post(postUrl2, FormData(Seq("owner" -> owner2))) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }

      "return a 401 if the owner already exists with body request" in {
        Post(postUrl2, HttpEntity(`application/json`, owner3)) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }

      """return a 401 if the new owner is conflicting with an existing one with
      query param""" in {
        Post(conflictingPostUrl1) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }

      """return a 401 if the new owner is conflicting with an existing one with
      form data""" in {
        Post(postUrl2, FormData(Seq("owner" -> faultyOwner2))) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }

      """return a 401 if the new owner is conflicting with an existing one with
      body request""" in {
        Post(postUrl2, HttpEntity(`application/json`, faultyOwner3)) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("This owner is conflicting with an existing one")
        }
      }
    }

    "for DELETE requests with key param" should {

      "return a 401 if the key provided is not super" in {
        Delete(deleteUrl + readKey) ~> addHeader("api_key", notSuperKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must contain("You do not have sufficient privileg")
        }
      }

      "return a 404 if the key is not found" in {
        Delete(deleteUrl + UUID.randomUUID().toString) ~>
        addHeader("api_key", superKey) ~> sealRoute(routes) ~> check {
          status === NotFound
          responseAs[String] must contain("API key not found")
        }
      }

      "return a 200 if the key is found and sufficient privileges" in {
        Delete(deleteUrl + readKey) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === OK
          responseAs[String] must contain("API key successfully deleted")
        }
      }
    }

    "for DELETE requests with owner param" should {

      "return a 401 if the key provided is not super" in {
        Delete(postUrl1) ~> addHeader("api_key", notSuperKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must contain("You do not have sufficient privileg")
        }
      }

      "return a 200 if there are keys associated with this owner" in {
        Delete(postUrl1) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === OK
          responseAs[String] must contain("API key deleted for ")
        }
      }

      "return a 404 if there are no keys associated with this owner" in {
        Delete(postUrl1) ~> addHeader("api_key", superKey) ~>
        sealRoute(routes) ~> check {
          status === NotFound
          responseAs[String] must contain("Owner not found")
        }
      }
    }
  }
}
