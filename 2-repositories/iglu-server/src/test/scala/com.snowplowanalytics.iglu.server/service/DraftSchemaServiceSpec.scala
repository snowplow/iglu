/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
package service

// Scala
import scala.concurrent.duration._
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Akka
import akka.actor.{ActorRef, Props}

// this project
import com.snowplowanalytics.iglu.server.actor.{ApiKeyActor, SchemaActor}

// Akka Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.server.Route

// Specs2
import org.specs2.mutable.Specification


class DraftSchemaServiceSpec extends Specification
  with Api with Specs2RouteTest with SetupAndDestroy {

  override def afterAll() = super.afterAll()
  val schemaActor: ActorRef = system.actorOf(Props(classOf[SchemaActor], config), "schemaActor4")
  val apiKeyActor: ActorRef = system.actorOf(Props(classOf[ApiKeyActor], config), "apiKeyActor4")

  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(20 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val writeKey = "a89c5f27-fe76-4754-8a07-d41884af1074"
  val faultyKey = "51ffa158-ba4b-4e27-a4ff-dfb5639b5453"
  val wrongVendorKey = "83e7c051-cd68-4e44-8b36-09182fa158d5"
  val notUuidKey = "83e7c051-cd68-4e44-8b36-09182f8d5"

  val vendor = "com.snowplowanalytics.snowplow"
  val vendor2 = "com.snowplowanalytics.self-desc"
  val otherVendor = "com.benfradet.ben"
  val otherVendor2 = "com.benfradet.snowplow"
  val name = "ad_click"
  val name2 = "ad_click2"
  val format = "jsonschema"
  val format2 = "jsontable"
  val version = "1-0-0"
  val version2 = "1-0-1"

  val draftNumber1 = "1"
  val draftNumber2 = "2"

  val validSchema =
    """{
      "$schema" : "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",""" + s"""
      "self": {
        "vendor": "$vendor",
        "name": "$name",
        "format": "$format",
        "version": "$version"
      }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"

  val validSchemaUri = URLEncoder.encode(validSchema, StandardCharsets.UTF_8.toString).toLowerCase
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22")

  val start = "/api/draft/"

  //get urls
  val publicSchemasUrl = s"${start}public"
  val metaPublicSchemasUrl = s"${publicSchemasUrl}?filter=metadata"

  val url = s"${start}${vendor}/${name}/${format}/$draftNumber1"
  val publicUrl = s"${start}${otherVendor}/${name}/${format}/${draftNumber1}"
  val multiUrl = s"${url},${vendor}/${name2}/${format}/${version}"
  val multiPublicUrl = s"${url},${otherVendor}/${name}/${format}/${version}"
  val faultyUrl = s"${start}${vendor}/${name}/jsonchema/${draftNumber1}"
  val multiVersionUrl = s"${url},${version2}"
  val multiVersionPublicUrl = s"${publicUrl},${version2}"
  val metaUrl = s"${url}?filter=metadata"
  val metaPublicUrl = s"${publicUrl}?filter=metadata"
  val metaMultiUrl = s"${multiUrl}?filter=metadata"
  val metaMultiPublicUrl = s"${multiPublicUrl}?filter=metadata"
  val metaMultiVersionUrl = s"${multiVersionUrl}?filter=metadata"
  val metaMultiVersionPublicUrl = s"${multiVersionPublicUrl}?filter=metadata"

  val multiVendor = s"${start}${vendor},${vendor2}/${name}/${format}/${version}"
  val multiVendorPublic =
    s"${start}${otherVendor},${otherVendor2}/${name}/${format}/${version}"
  val metaMultiVendor = s"${multiVendor}?filter=metadata"
  val metaMultiVendorPublic = s"${multiVendorPublic}?filter=metadata"
  val multiFormat = s"${start}${vendor}/${name}/${format},${format2}/${version}"
  val multiFormatPublic =
    s"${start}${otherVendor}/${name}/${format},${format2}/${version}"
  val metaMultiFormat = s"${multiFormat}?filter=metadata"
  val metaMultiFormatPublic = s"${multiFormatPublic}?filter=metadata"
  var multiName = s"${start}${vendor}/${name},${name2}/${format}/${version}"
  val multiNamePublic =
    s"${start}${otherVendor}/${name},${name2}/${format}/${version}"
  val metaMultiName = s"${multiName}?filter=metadata"
  val metaMultiNamePublic = s"${multiNamePublic}?filter=metadata"

  val vendorUrl = s"${start}${vendor}"
  val vendorPublicUrl = s"${start}${otherVendor}"
  val multiVendorPublicUrl = s"${vendorPublicUrl},${otherVendor2}"
  val metaVendorUrl = s"${vendorUrl}?filter=metadata"
  val metaVendorPublicUrl = s"${vendorPublicUrl}?filter=metadata"
  val metaMultiVendorPublicUrl = s"${multiVendorPublicUrl}?filter=metadata"

  val nameUrl = s"${start}${vendor}/${name}"
  val namePublicUrl = s"${start}${otherVendor}/${name}"
  val metaNameUrl = s"${nameUrl}?filter=metadata"
  val metaNamePublicUrl = s"${namePublicUrl}?filter=metadata"

  val formatUrl = s"${start}${vendor}/${name}/${format}"
  val formatPublicUrl = s"${start}${otherVendor}/${name}/${format}"
  val multiFormatUrl = s"${formatUrl},${format2}"
  val multiFormatPublicUrl = s"${formatPublicUrl},${format2}"
  val metaFormatUrl = s"${formatUrl}?filter=metadata"
  val metaFormatPublicUrl = s"${formatPublicUrl}?filter=metadata"
  val metaMultiFormatUrl = s"${multiFormatUrl}?filter=metadata"
  val metaMultiFormatPublicUrl = s"${multiFormatPublicUrl}?filter=metadata"

  val otherVendorUrl = s"${start}com.benfradet.project"
  val otherNameUrl = s"${start}com.benfradet.project/${name}"
  val otherFormatUrl = s"${start}com.benfradet.project/${name}/${format}"

  val metadataIncludedUrl = s"$url?metadata=1"

  //post urls
  val postUrl1 = s"$start$vendor/unit_test1/$format/$draftNumber1"
  val postUrl2 = s"$start$vendor/unit_test2/$format/$draftNumber1" +
    s"?schema=$validSchemaUri"
  val postUrl3 = s"$start$vendor/unit_test3/$format/$draftNumber1"
  val postUrl4 = s"$start$vendor/unit_test4/$format/$draftNumber1" +
    s"?schema=$validSchemaUri"
  val postUrl6 = s"$url?schema=$validSchemaUri"
  val postUrl7 = s"$start$vendor/unit_test7/$format/$draftNumber1" +
    s"?schema=$invalidSchemaUri"
  val postUrl8 = s"$start$vendor/unit_test8/$format/$draftNumber1" +
    s"?schema=$notJson"
  val postUrl9 = s"${start}${vendor}/unit_test9/${format}/${draftNumber1}" +
    s"?isPublic=true"
  val postUrl10 = s"${start}${vendor}/unit_test10/${format}/${draftNumber1}" +
    s"?schema=${validSchemaUri}&isPublic=true"
  val postUrl11 = s"${start}${vendor}/unit_test11/${format}/${draftNumber1}"
  val postUrl12 = s"${start}${vendor}/unit_test12/${format}/${draftNumber1}"

  //put urls
  val putUrl1 = s"${start}${vendor}/unit_test13/${format}/${draftNumber1}"
  val putUrl2 = s"${start}${vendor}/unit_test14/${format}/${draftNumber1}" +
    s"?schema=${validSchemaUri}"
  val putUrl3 = s"${start}${vendor}/unit_test15/${format}/${draftNumber1}"

  sequential

  "DraftSchemaService" should {

    "for GET requests" should {

      "for the /api/draft/public endpoint" should {

        "return a proper catalog of public schemas" in {
          Get(publicSchemasUrl) ~> addHeader("apikey", readKey) ~> routes ~>
            check {
              status === OK
              contentType === `application/json`
              responseAs[String] must contain(otherVendor)
            }
        }

        "return proper metadata for every public schema" in {
          Get(metaPublicSchemasUrl) ~> addHeader("apikey", readKey) ~>
            routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(otherVendor)
          }
        }
      }

      "for version based urls" should {

        "return a proper json for well-formed single GET requests" +
          s"($url)" in {
          Get(url) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(name)
          }
        }

        s"return a proper json for a public draft schema (${publicUrl})" in {
          Get(publicUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(otherVendor)
          }
        }

        "return proper metadata for well-formed single GET requests" +
          s"($metaUrl)" in {
          Get(metaUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(vendor) and contain(name) and
              contain(format) and contain(draftNumber1)
          }
        }

        "return schema without metadata for GET " +
          s"($url)" in {
          Get(url) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(vendor) and contain(name) and
              contain(format) and contain(draftNumber1) and not contain("metadata")
          }
        }

        "return schema with metadata for GET " +
          s"($metadataIncludedUrl)" in {
          Get(metadataIncludedUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(vendor) and contain(name) and
              contain(format) and contain(draftNumber1) and contain("metadata")
          }
        }

        s"return proper metadata for a public schema $metaPublicUrl" in {
          Get(metaPublicUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
            check {
              status === OK
              contentType === `application/json`
              responseAs[String] must contain(otherVendor) and contain(name) and
                contain(format) and contain(draftNumber1)
            }
        }

        "return a 404 for GET requests for which the schema is not in the db" in
          {
            Get(faultyUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must
                contain("There are no schemas available here")
            }
          }

        "return a 404 if no apikey is found in the db" in {
          Get(url) ~> addHeader("apikey", faultyKey) ~> Route.seal(routes) ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must
                contain("There are no schemas available here")
            }
        }

        "return a 404 if the API key provided is not an uuid" in {
          Get(url) ~> addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must
                contain("There are no schemas available here")
            }
        }

        "return a 404 if no apikey is provided" in {
          Get(url) ~> Route.seal(routes) ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        """return a 404 if the owner of the API key is not a prefix of the
          schema's vendor""" in {
          Get(url) ~> addHeader("apikey", wrongVendorKey) ~>
            Route.seal(routes) ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return a 200 if schema is public and no apikey is provided" in {
          Get(s"$start$otherVendor/$name2/$format/$draftNumber1") ~> Route.seal(routes) ~> check {
            status === OK
            contentType === `application/json`
          }
        }

        "return a 404 if schema is private and no apikey is provided" in {
          Get(s"$start$otherVendor/$name/$format/$draftNumber1") ~> Route.seal(routes) ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "leave GET requests to other paths unhandled" in {
          Get("/test") ~> routes ~> check {
            handled must beFalse
          }
        }
      }

      "for vendor based urls" should {

        "return the catalog of available schemas for this vendor" +
          s"(${vendorUrl})" in {
          Get(vendorUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return the catalog of available public schemas for another vendor" +
          s"(${vendorPublicUrl})" in {
          Get(vendorPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
            check {
              status === OK
              contentType === `application/json`
              responseAs[String] must contain(otherVendor) and contain(name) and
                contain(name2)
            }
        }

        "return metadata about every schema for this vendor" +
          s"(${metaVendorUrl})" in {
          Get(metaVendorUrl) ~> addHeader("apikey", readKey) ~> routes ~>
            check {
              status === OK
              contentType === `application/json`
              responseAs[String] must contain(vendor)
            }
        }

        "return metadata about every public schema for another vendor" +
          s"(${metaVendorPublicUrl})" in {
          Get(metaVendorPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
            check {
              status === OK
              contentType === `application/json`
              responseAs[String] must contain(otherVendor)
            }
        }

        "return a 404 for a vendor which has no schemas" in {
          Get(otherVendorUrl) ~> addHeader("apikey", wrongVendorKey) ~>
            routes ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return a 404 if the owner is not a prefix of the vendor" in {
          Get(vendorUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must contain("There are no schemas available here")
            }
        }
      }

      "for name based urls" should {

        "return a 200 if schema is public and no apikey is provided" in {
          Get(s"$start$otherVendor/$name2") ~> Route.seal(routes) ~> check {
            status === OK
            contentType === `application/json`
          }
        }

        "return a 404 if schema is private and no apikey is provided" in {
          Get(s"$start$otherVendor/$name") ~> Route.seal(routes) ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return the catalog of available schemas for this name" +
          s"(${nameUrl})" in {
          Get(nameUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        "return metadata about every schema having this vendor, name" +
          s"(${metaNameUrl})" in {
          Get(metaNameUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(vendor) and contain(name)
          }
        }

        "return a 404 for a vendor/name combination which has no schemas" in {
          Get(otherNameUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must
                contain("There are no schemas available here")
            }
        }

        "return a 404 if the owner is not a prefix of the vendor" in {
          Get(nameUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must contain("There are no schemas available here")
            }
        }


      }

      "for format based urls" should {

        "return the catalog of available schemas for this format" +
          s"(${formatUrl})" in {
          Get(formatUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            contentType === `application/json`
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        """return a 404 for a vendor/name/format combination which has
        no schemas""" in {
          Get(otherFormatUrl) ~> addHeader("apikey", wrongVendorKey) ~>
            routes ~> check {
            status === NotFound
            contentType === `application/json`
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return a 404 if the owner is not a prefix of the vendor" in {
          Get(formatUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
            check {
              status === NotFound
              contentType === `application/json`
              responseAs[String] must contain("There are no schemas available here")
            }
        }
      }
    }

    "for POST requests" should {

      //should be removed from db before running tests for now
      "return success if the schema is passed as form data" in {
        Post(postUrl1, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("apikey", writeKey) ~> Route.seal(routes) ~>
          check {
            status === Created
            contentType === `application/json`
            responseAs[String] must contain("The schema has been successfully added") and
              contain(vendor)
          }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as request body" in {
        Post(postUrl11, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as form data and is public" in {
        Post(postUrl9, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as query param and is public" in {
        Post(postUrl10) ~> addHeader("apikey", writeKey) ~>
          Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      """return success if the schemas is passed as request body and is
      public""" in {
        Post(postUrl12, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      """return a 400 if no form data or query param or body request is
      specified""" in {
        Post(postUrl3) ~> addHeader("apikey", writeKey) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("The schema provided is not valid")
        }
      }

      """return 401 if the API key doesn't have sufficient permissions with
      query param""" in {
        Post(postUrl4) ~> addHeader("apikey", readKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions
      with form data""" in {
        Post(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", readKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions with
      body request""" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", readKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 400 if no apikey is specified with query param" in {
        Post(postUrl4) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 400 if no apikey is specified with form data" in {
        Post(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 400 if no apikey is specified with body request" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 401 if the API key is not an uuid with query param" in {
        Post(postUrl4) ~> addHeader("apikey", notUuidKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with form data" in {
        Post(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with body request" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with query param""" in {
        Post(postUrl6) ~> addHeader("apikey", wrongVendorKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with form data""" in {
        Post(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", wrongVendorKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with body request""" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", wrongVendorKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 400 if the supplied string is not a schema with query param" in
        {
          Post(postUrl8) ~> addHeader("apikey", writeKey) ~> Route.seal(routes) ~>
            check {
              status === BadRequest
              contentType === `text/plain(UTF-8)`
              responseAs[String] must contain("The schema provided is not valid")
            }
        }

      "return a 400 if the supplied string is not a schema with form data" in {
        Post(postUrl3, FormData(Map("schema" -> HttpEntity(`text/plain(UTF-8)`, notJson)))) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      """return a 400 if the supplied string is not a schema with body
      request""" in {
        Post(postUrl3, HttpEntity(`application/json`, notJson)) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("The schema provided is not valid")
        }
      }
    }

    "for PUT requests" should {

      "return a 201 if the schema doesnt already exist with form data" in {
        Put(putUrl1, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      "return a 201 if the schema doesnt already exist with query param" in {
        Put(putUrl2) ~> addHeader("apikey", writeKey) ~> Route.seal(routes) ~>
          check {
            status === Created
            contentType === `application/json`
            responseAs[String] must contain("The schema has been successfully added") and
              contain(vendor)
          }
      }

      "return a 201 if the schema doesnt already exist with body request" in {
        Put(putUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("The schema has been successfully added") and
            contain(vendor)
        }
      }

      """return a 400 if no form data or query param or body request is
      specified""" in {
        Put(postUrl3) ~> addHeader("apikey", writeKey) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("The schema provided is not valid")
        }
      }

      """return 401 if the API key doesn't have sufficient permissions with
      query param""" in {
        Put(postUrl4) ~> addHeader("apikey", readKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions
      with form data""" in {
        Put(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", readKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions with
      body request""" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", readKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 400 if no apikey is specified with query param" in {
        Put(postUrl4) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 400 if no apikey is specified with form data" in {
        Put(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 400 if no apikey is specified with body request" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("Request is missing required HTTP header 'apikey'")
        }
      }

      "return a 401 if the API key is not an uuid with query param" in {
        Put(postUrl4) ~> addHeader("apikey", notUuidKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with form data" in {
        Put(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with body request" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with query param""" in {
        Put(postUrl6) ~> addHeader("apikey", wrongVendorKey) ~>
          Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with form data""" in {
        Put(postUrl3, FormData(Map("schema" -> HttpEntity(`application/json`, validSchema)))) ~>
          addHeader("apikey", wrongVendorKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with body request""" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
          addHeader("apikey", wrongVendorKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 400 if the supplied string is not a schema with query param" in
        {
          Put(postUrl8) ~> addHeader("apikey", writeKey) ~> Route.seal(routes) ~>
            check {
              status === BadRequest
              contentType === `text/plain(UTF-8)`
              responseAs[String] must contain("The schema provided is not valid")
            }
        }

      "return a 400 if the supplied string is not a schema with form data" in {
        Put(postUrl3, FormData(Map("schema" -> HttpEntity(`text/plain(UTF-8)` , notJson)))) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      """return a 400 if the supplied string is not a schema with body
      request""" in {
        Put(postUrl3, HttpEntity(`application/json`, notJson)) ~>
          addHeader("apikey", writeKey) ~> Route.seal(routes) ~> check {
          status === BadRequest
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("The schema provided is not valid")
        }
      }
    }
  }
}
