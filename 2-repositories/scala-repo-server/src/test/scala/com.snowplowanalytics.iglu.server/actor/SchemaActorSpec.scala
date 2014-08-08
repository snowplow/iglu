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
import actor.SchemaActor
import actor.SchemaActor._

// Akka
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import akka.util.Timeout

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

class SchemaActorSpec extends TestKit(ActorSystem()) with SpecificationLike
  with ImplicitSender with NoTimeConversions {

  implicit val timeout = Timeout(20.seconds)

  val schema = TestActorRef(new SchemaActor)

  val vendor = "com.unittest"
  val faultyVendor = "com.test"
  val name = "unit_test3"
  val faultyName = "unit_test4"
  val format = "jsonschema"
  val version = "1-0-0"
  val schemaDef = """{ "some" : "json" }"""
  val innerSchema = """"some" : "json""""
  val validSchema = 
  """{
    "self": {
      "vendor": "com.snowplowanalytics.snowplow",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    }
  }"""
  val notJson = "not json"

  sequential

  "SchemaActor" should {

    "for AddSchema" should {

      "return a 200 if the schema doesnt already exist" in {
        val future = schema ? AddSchema(vendor, name, format, version,
          schemaDef)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain("Schema added successfully")
      }

      "return a 401 if the schema already exists" in {
        val future = schema ? AddSchema(vendor, name, format, version,
          schemaDef)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(Unauthorized)
        result must contain("This schema already exists")
      }
    }

    "for GetSchema" should {

      "return a 200 if the schema exists" in {
        val future = schema ? GetSchema(vendor, name, format, version)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(innerSchema)
      }

      "return a 404 if the schema doesnt exist" in {
        val future = schema ? GetSchema(vendor, faultyName, format, version)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas available here")
      }
    }

    "for GetMetadata" should {

      "return a 200 if the schema exists" in {
        val future = schema ? GetMetadata(vendor, name, format, version)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(vendor) and contain(name) and contain(format) and
          contain(version)
      }

      "return a 404 if the schema doesnt exist" in {
        val future = schema ? GetMetadata(vendor, faultyName, format, version)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas available here")
      }
    }

    "for GetSchemasFromFormat" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetSchemasFromFormat(vendor, name, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(innerSchema)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetSchemasFromFormat(vendor, faultyName, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetMetadataFromFormat" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetMetadataFromFormat(vendor, name, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(vendor) and contain(name) and contain(format)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetMetadataFromFormat(vendor, faultyName, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetSchemasFromName" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetSchemasFromName(vendor, name)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(innerSchema)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetSchemasFromName(vendor, faultyName)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetMetadataFromName" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetMetadataFromName(vendor, name)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(vendor) and contain(name)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetMetadataFromName(vendor, faultyName)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetSchemasFromVendor" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetSchemasFromVendor(vendor)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(innerSchema)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetSchemasFromVendor(faultyVendor)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor")
      }
    }

    "for GetMetadataFromVendor" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetMetadataFromVendor(vendor)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(vendor)
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetMetadataFromVendor(faultyVendor)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(NotFound)
        result must contain("There are no schemas for this vendor")
      }
    }

    "for Validate" should {

      "return a 200 if the json provided is self-describing" in {
        val future = schema ? Validate(validSchema)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(OK)
        result must contain(validSchema)
      }

      "return a 400 if the json provided is not self-describing" in {
        val future = schema ? Validate(schemaDef)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be(BadRequest)
        result must contain("The json provided is not a valid self-describing")
      }

      "return a 400 if the string provided is not a json" in {
        val future = schema ? Validate(notJson)
        val Success((status: StatusCode, result: String)) = future.value.get
        status must be (BadRequest)
        result must contain("The json provided is not valid")
      }
    }
  }
}
