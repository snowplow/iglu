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
import SchemaActor._

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
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http.StatusCode
import spray.http.StatusCodes._

class SchemaActorSpec extends TestKit(ActorSystem()) with SetupAndDestroy
  with ImplicitSender with NoTimeConversions {

  implicit val timeout = Timeout(20.seconds)

  val schema = TestActorRef(new SchemaActor)

  val owner = "com.unittest"
  val otherOwner = "com.benfradet"
  val permission = "write"
  val isPublic = false

  val vendor = "com.unittest"
  val vendors = List(vendor)
  val otherVendor = "com.benfradet"
  val otherVendors = List(otherVendor)
  val faultyVendor = "com.test"
  val faultyVendors = List(faultyVendor)
  val name = "unit_test3"
  val names = List(name)
  val name2 = "unit_test6"
  val faultyName = "unit_test4"
  val faultyNames = List(faultyName)
  val otherName = "unit_test5"
  val otherNames = List(otherName)
  val format = "jsonschema"
  val notSupportedFormat = "notSupportedFormat"
  val formats = List(format)
  val version = "1-0-0"
  val versions = List(version)

  val invalidSchema = """{ "some" : "json" }"""
  val innerSchema = "\"some\" : \"json\""
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
  val validInstance = """{ "targetUrl": "somestr" }"""

  sequential

  "SchemaActor" should {

    "for AddSchema" should {

      "return a 201 if the schema doesnt already exist and is private" in {
        val future = schema ? AddSchema(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Created
        result must contain("Schema successfully added") and contain(vendor)
      }

      "return a 201 if the schema doesnt already exist and is public" in {
        val future = schema ? AddSchema(otherVendor, otherName, format, version,
          invalidSchema, otherOwner, permission, !isPublic)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Created
        result must contain("Schema successfully added") and
          contain(otherVendor)
      }

      "return a 401 if the schema already exists" in {
        val future = schema ? AddSchema(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("This schema already exists")
      }
    }

    "for UpdateSchema" should {

      "return a 200 if the schema already exists" in {
        val future = schema ? UpdateSchema(vendor, name, format, version,
          invalidSchema, owner, permission, isPublic)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain("Schema successfully updated") and contain(vendor)
      }

      "returna 201 if the schema doesnt already exist" in {
        val future = schema ? UpdateSchema(vendor, name2, format, version,
          invalidSchema, owner, permission, isPublic)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Created
        result must contain("Schema successfully added") and contain(vendor)
      }
    }

    "for GetPublicSchemas" should {

      "return a 200 if there are public schemas available" in {
        val future = schema ? GetPublicSchemas(owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }
    }

    "for GetPublicMetadata" should {

      "return a 200 if there are public schemas available" in {
        val future = schema ? GetPublicMetadata(owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        result must contain(otherVendor) and contain(otherName) and
          contain(format) and contain(version)
      }
    }

    "for GetSchema" should {

      "return a 200 if the schema exists and is private" in {
        val future = schema ?
          GetSchema(vendors, names, formats, versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      "return a 200 if the schema exists and is public" in {
        val future = schema ? GetSchema(otherVendors, otherNames, formats,
          versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val future = schema ?
          GetSchema(vendors, names, formats, versions, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema doesnt exist" in {
        val future = schema ?
          GetSchema(vendors, faultyNames, formats, versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas available here")
      }
    }

    "for GetMetadata" should {

      "return a 200 if the schema exists" in {
        val future = schema ?
          GetMetadata(vendors, names, formats, versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(vendor) and contain(name) and contain(format) and
          contain(version)
      }

      "return a 200 if the schema exists and is public" in {
        val future = schema ? GetMetadata(otherVendors, otherNames, formats,
          versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(otherVendor) and contain(otherName) and
          contain(format) and contain(version)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schema
      is private""" in {
        val future = schema ?
          GetSchema(vendors, names, formats, versions, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if the schema doesnt exist" in {
        val future = schema ? GetMetadata(vendors, faultyNames, formats,
          versions, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas available here")
      }
    }

    "for GetSchemasFromFormat" should {

      "return a 200 if there are schemas available" in {
        val future = schema ?
          GetSchemasFromFormat(vendors, names, formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ? GetSchemasFromFormat(otherVendors, otherNames,
          formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetSchemasFromFormat(vendors, names, formats, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ?
          GetSchemasFromFormat(vendors, faultyNames, formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetMetadataFromFormat" should {

      "return a 200 if there are schemas available" in {
        val future = schema ?
          GetMetadataFromFormat(vendors, names, formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(vendor) and contain(name) and contain(format)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ? GetMetadataFromFormat(otherVendors, otherNames,
          formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(otherVendor) and contain(otherName) and
          contain(format)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetMetadataFromFormat(vendors, names, formats, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ? GetMetadataFromFormat(vendors, faultyNames,
          formats, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetSchemasFromName" should {

      "return a 200 if there are schemas available" in {
        val future = schema ?
          GetSchemasFromName(vendors, names, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ?
          GetSchemasFromName(otherVendors, otherNames, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetSchemasFromName(vendors, names, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ?
          GetSchemasFromName(vendors, faultyNames, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetMetadataFromName" should {

      "return a 200 if there are schemas available" in {
        val future = schema ?
          GetMetadataFromName(vendors, names, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(vendor) and contain(name)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ?
          GetMetadataFromName(otherVendors, otherNames, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(otherVendor) and contain(otherName)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetMetadataFromName(vendors, names, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ?
          GetMetadataFromName(vendors, faultyNames, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor, name")
      }
    }

    "for GetSchemasFromVendor" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetSchemasFromVendor(vendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ?
          GetSchemasFromVendor(otherVendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(innerSchema)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetSchemasFromVendor(vendors,  otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ?
          GetSchemasFromVendor(faultyVendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor")
      }
    }

    "for GetMetadataFromVendor" should {

      "return a 200 if there are schemas available" in {
        val future = schema ? GetMetadataFromVendor(vendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(vendor)
      }

      "return a 200 if there are schemas available and they are public" in {
        val future = schema ?
          GetMetadataFromVendor(otherVendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(otherVendor)
      }

      """return a 401 if the owner is not a prefix of the vendor and the schemas
      are private""" in {
        val future = schema ?
          GetMetadataFromVendor(vendors, otherOwner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === Unauthorized
        result must contain("You do not have sufficient privileges")
      }

      "return a 404 if there are no schemas available" in {
        val future = schema ?
          GetMetadataFromVendor(faultyVendors, owner, permission)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("There are no schemas for this vendor")
      }
    }

    "for Validate" should {
      val vendor = "com.snowplowanalytics.snowplow"
      val name = "ad_click"

      "return a 200 if the instance is valid against the schema" in {
        val future = schema ? Validate(vendor, name, format, version,
          validInstance)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain("The instance provided is valid against the schema")
      }

      "return a 400 if the instance is not valid against the schema" in {
        val future = schema ? Validate(vendor, name, format, version,
          invalidSchema)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === BadRequest
        result must
          contain("The instance provided is not valid against the schema")
      }

      "return a 400 if the instance provided is not valid" in {
        val future = schema ? Validate(vendor, name, format, version, notJson)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === BadRequest
        result must contain("The instance provided is not valid")
      }

      "return a 404 if the schema to validate against was not found" in {
        val future = schema ? Validate("com.unittest", name, format, version,
          validInstance)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === NotFound
        result must contain("The schema to validate against was not found")
      }
    }

    "for ValidateSchema" should {

      """return a 200 if the schema provided is self-describing and gives back
      the schema""" in {
        val future = schema ? ValidateSchema(validSchema, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must contain(validSchema)
      }

      "return a 200 if the schema provided is self-describing" in {
        val future = schema ? ValidateSchema(validSchema, format, false)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === OK
        result must
          contain("The schema provided is a valid self-describing schema")
      }

      "return a 400 if the schema provided is not self-describing" in {
        val future = schema ? ValidateSchema(invalidSchema, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === BadRequest
        result must
          contain("The schema provided is not a valid self-describing")
      }

      "return a 400 if the string provided is not valid" in {
        val future = schema ? ValidateSchema(notJson, format)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === BadRequest
        result must contain("The schema provided is not valid")
      }

      "return a 400 if the schema format provided is not supported" in {
        val future = schema ? ValidateSchema(validSchema, notSupportedFormat)
        val Success((status: StatusCode, result: String)) = future.value.get
        status === BadRequest
        result must contain("The schema format provided is not supported")
      }
    }
  }
}
