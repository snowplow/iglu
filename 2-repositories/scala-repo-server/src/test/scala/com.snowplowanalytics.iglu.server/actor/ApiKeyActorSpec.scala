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
package test.actor

// This project
import actor.ApiKeyActor
import actor.ApiKeyActor._

// Akka
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import akka.util.Timeout

// Java
import java.util.UUID

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
import spray.json._
import DefaultJsonProtocol._

class ApiKeyActorSpec extends TestKit(ActorSystem()) with SpecificationLike
  with ImplicitSender with NoTimeConversions {

  implicit val timeout = Timeout(10.seconds)

  val key = TestActorRef(new ApiKeyActor)

  var readKey = ""
  var writeKey = ""

  sequential

  "ApiKeyActor" should {

    "for AddBothKey" should {

      "return a 200 for a non-conflicting owner" in {
        val future = key ? AddBothKey("com.unit.test")
        val Success((status: StatusCode, result: String)) = future.value.get
        val map = result.parseJson.convertTo[Map[String, String]]
        readKey = map getOrElse("read", "")
        writeKey = map getOrElse("write", "")
        status must be(OK)
        result must contain("read") and contain("write")
      }

      "return a 401 if the owner is conflicting" in {
        val future = key ? AddBothKey("com.unit")
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(Unauthorized)
        result must contain("This vendor is conflicting with an existing one")
      }
    }

    "for GetKey" should {

      "return a valid (owner, permission) pair" in {
        val future = key ? GetKey(UUID.fromString(readKey))
        val Success(Some((owner: String, permission: String))) =
          future.value.get
        owner must contain("com.unit.test")
        permission must contain("read")
      }

      "return None if the api key is not found" in {
        val future = key ? GetKey(UUID.randomUUID())
        val Success(None) = future.value.get
        success
      }
    }

    "for DeleteKey" should {

      "return a 200 if the key exists" in {
        val future = key ? DeleteKey(UUID.fromString(readKey))
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain("Api key successfully deleted")

        val future2 = key ? DeleteKey(UUID.fromString(writeKey))
        val Success((status2: StatusCode, result2: String)) = future.value.get
        status must be(OK)
        result must contain("Api key successfully deleted")
      }

      "return a 404 if the key doesnt exist" in {
        val future = key ? DeleteKey(UUID.fromString(readKey))
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("Api key not found")
      }
    }
  }
}