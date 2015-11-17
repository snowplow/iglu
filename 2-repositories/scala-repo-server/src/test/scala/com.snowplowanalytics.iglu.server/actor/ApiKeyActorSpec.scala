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
package actor

// This project
import ApiKeyActor._

// Akka
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import akka.util.Timeout

// Java
import java.util.UUID

// Json4s
import org.json4s._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

// Scala
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

// Specs2
import org.specs2.mutable.SpecificationLike
import org.specs2.time.NoTimeConversions

// Spray
import spray.http.StatusCode
import spray.http.StatusCodes._

class ApiKeyActorSpec extends TestKit(ActorSystem()) with SpecificationLike
  with ImplicitSender with NoTimeConversions {

  implicit val timeout = Timeout(20.seconds)

  val key = TestActorRef(new ApiKeyActor)

  implicit val formats = DefaultFormats

  val vendorPrefix = "com.actor.unit.test"
  val faultyVendorPrefix = "com.actor.unit"

  val notUuidKey = "this-is-not-an-uid"

  var readKey = ""
  var writeKey = ""

  sequential

  "ApiKeyActor" should {

    "for AddBothKey" should {

      "return a 200 for a non-conflicting vendor prefix" in {
        val future = key ? AddBothKey(vendorPrefix)
        val Success((status: StatusCode, result: String)) = future.value.get

        val map = parse(result).extract[Map[String, String]]
        readKey = map getOrElse("read", "")
        writeKey = map getOrElse("write", "")
        status === Created
        result must contain("read") and contain("write")
      }

      "return a 401 if the vendor prefix is conflicting" in {
        val future = key ? AddBothKey(faultyVendorPrefix)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must
          contain("This vendor prefix is conflicting with an existing one")
      }
    }

    "for GetKey" should {

      "return a valid (vendor prefix, permission) pair" in {
        val future = key ? GetKey(readKey)
        val Success(Some((vp: String, permission: String))) =
          future.value.get
        vp must contain(vendorPrefix)
        permission must contain("read")
      }

      "return None if the API key is not found" in {
        val future = key ? GetKey(UUID.randomUUID.toString)
        val Success(None) = future.value.get
        success
      }

      "return None if the API key is not an uuid" in {
        val future = key ? GetKey(notUuidKey)
        val Success(None) = future.value.get
        success
      }
    }

    "for DeleteKey" should {

      "return a 200 if the key exists" in {
        val future = key ? DeleteKey(readKey)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain("API key successfully deleted")
      }

      "return a 404 if the key doesnt exist" in {
        val future = key ? DeleteKey(readKey)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("API key not found")
      }

      "return a 401 if the key is not an uuid" in {
        val future = key ? DeleteKey(notUuidKey)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("The API key provided is not an UUID")
      }
    }

    "for DeleteKeys" should {

      "return a 200 if there are keys associated with this vendor prefix" in {
        val future = key ? DeleteKeys(vendorPrefix)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain("API key deleted for " + vendorPrefix)
      }

      "return a 404 if there are no API keys associated with this vendor" +
      "prefix" in {
        val future = key ? DeleteKeys(vendorPrefix)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("Vendor prefix not found")
      }
    }
  }
}
