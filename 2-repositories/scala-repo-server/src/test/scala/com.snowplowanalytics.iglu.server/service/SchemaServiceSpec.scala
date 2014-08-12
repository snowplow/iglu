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

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

// Spray
import spray.http._
import StatusCodes._
import spray.testkit.Specs2RouteTest

class SchemaServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

  val readKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val writeKey = "a89c5f27-fe76-4754-8a07-d41884af1074"
  val faultyKey = "51ffa158-ba4b-4e27-a4ff-dfb5639b5453"
  val wrongVendorKey = "83e7c051-cd68-4e44-8b36-09182fa158d5"
  val notUuidKey = "83e7c051-cd68-4e44-8b36-09182f8d5"

  val vendor = "com.snowplowanalytics.snowplow"
  val vendor2 = "com.snowplowanalytics.self-desc"
  val name = "ad_click"
  val name2 = "ad_click2"
  val format = "jsonschema"
  val format2 = "jsontable"
  val version = "1-0-0"
  val version2 = "1-0-1"

  val validSchema = 
    s"""{
      "self": {
        "vendor": "${vendor}",
        "name": "${name}",
        "format": "${format}",
        "version": "${version}"
      }
    }"""
  val invalidSchema = """{ "some": "invalid schema" }"""
  val notJson = "notjson"

  val validSchemaUri = validSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22").replaceAll("\n", "%0A")
  val invalidSchemaUri = invalidSchema.replaceAll(" ", "%20").
    replaceAll("\"", "%22")

  val start = "/api/schemas/"

  //get urls
  val url = s"${start}${vendor}/${name}/${format}/${version}"
  val faultyUrl = s"${start}${vendor}/${name}/jsonchema/${version}"
  val multiVersionUrl = s"${url},${version2}"
  val metaUrl = s"${url}?filter=metadata"
  val metaMultiVersionUrl = s"${multiVersionUrl}?filter=metadata"

  val multiVendor = s"${start}${vendor},${vendor2}/${name}/${format}/${version}"
  val metaMultiVendor = s"${multiVendor}?filter=metadata"
  val multiFormat = s"${start}${vendor}/${name}/${format},${format2}/${version}"
  val metaMultiFormat = s"${multiFormat}?filter=metadata"
  var multiName = s"${start}${vendor}/${name},${name2}/${format}/${version}"
  val metaMultiName = s"${multiName}?filter=metadata"

  val vendorUrl = s"${start}${vendor}"
  val multiVendorUrl = s"${vendorUrl},${vendor2}"
  val metaVendorUrl = s"${vendorUrl}?filter=metadata"
  val metaMultiVendorUrl = s"${multiVendorUrl}?filter=metadata"

  val nameUrl = s"${start}${vendor}/${name}"
  val multiNameUrl = s"${nameUrl},${name2}"
  val metaNameUrl = s"${nameUrl}?filter=metadata"
  val metaMultiNameUrl = s"${multiNameUrl}?filter=metadata"

  val formatUrl = s"${start}${vendor}/${name}/${format}"
  val multiFormatUrl = s"${formatUrl},${format2}"
  val metaFormatUrl = s"${formatUrl}?filter=metadata"
  val metaMultiFormatUrl = s"${multiFormatUrl}?filter=metadata"

  val otherVendorUrl = s"${start}com.benfradet.project"
  val otherNameUrl = s"${start}com.benfradet.project/${name}"
  val otherFormatUrl = s"${start}com.benfradet.project/${name}/jsonschema"

  //post urls
  val postUrl1 = s"${start}${vendor}/unit_test1/${format}/${version}"
  val postUrl2 = s"${start}${vendor}/unit_test2/${format}/${version}" +
    s"?json=${validSchemaUri}"
  val postUrl3 = s"${start}${vendor}/unit_test3/${format}/${version}"
  val postUrl4 = s"${start}${vendor}/unit_test4/${format}/${version}" +
    s"?json=${validSchemaUri}"
  val postUrl6 = s"${url}?json=${validSchemaUri}"
  val postUrl7 = s"${start}${vendor}/unit_test7/${format}/${version}" +
    s"?json=${invalidSchemaUri}"
  val postUrl8 = s"${start}${vendor}/unit_test8/${format}/${version}" +
    s"?json=${notJson}"

  sequential

  "SchemaService" should {

    "for GET requests" should {

      "for version based url" should {

        "return a proper json for well-formed single GET requests" in {
          Get(url) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(name)
          }
        }

        "return a proper json for a multi GET requests" in {
          Get(multiVersionUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        "return a proper json for multi format url" in {
          Get(multiFormat) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(format) and contain(format2)
          }
        }

        "return a proper json for multi name url" in {
          Get(multiName) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return a proper json for multi vendor url" in {
          Get(multiVendor) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        "return proper metadata for well-formed single GET requests" in {
          Get(metaUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(vendor) and contain(name) and
              contain(format) and contain(version)
          }
        }

        "return proper metadata for a multi GET request" in {
          Get(metaMultiVersionUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        "return proper metadata for multi format url" in {
          Get(metaMultiFormat) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(format) and contain(format2)
          }
        }

        "return proper metadata for multi name url" in {
          Get(metaMultiName) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return proper metadata for multi vendor url" in {
          Get(metaMultiVendor) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        "return a 404 for GET requests for which the schema is not in the db" in
        {
          Get(faultyUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === NotFound
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return a 401 if no api_key is found in the db" in {
          Get(url) ~> addHeader("api_key", faultyKey) ~> sealRoute(routes) ~>
            check {
              status === Unauthorized
              responseAs[String] must
                contain("The supplied authentication is invalid")
            }
        }

        "return a 401 if the API key provided is not an uuid" in {
          Get(url) ~> addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~>
            check {
              status === Unauthorized
              responseAs[String] must
                contain("The supplied authentication is invalid")
            }
        }

        "return a 401 if no api_key is provided" in {
          Get(url) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The resource requires authentication")
          }
        }

        """return a 401 if the owner of the API key is not a prefix of the
          schema's vendor""" in {
            Get(url) ~> addHeader("api_key", wrongVendorKey) ~>
              sealRoute(routes) ~> check {
                status === Unauthorized
                responseAs[String] must
                  contain("You do not have sufficient privileges")
              }
        }

        "leave GET requests to other paths unhandled" in {
          Get("/test") ~> routes ~> check {
            handled must beFalse
          }
        }
      }

      "for vendor based url" should {

        "return the catalog of available schemas for this vendor" in {
          Get(vendorUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return the catalog of available schemas for those vendors" in {
          Get(multiVendorUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        "return metadata about every schema for this vendor" in {
          Get(metaVendorUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor)
          }
        }

        "return metadata about every schema for those vendors" in {
          Get(metaMultiVendorUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        "return a 404 for a vendor which has no schemas" in {
          Get(otherVendorUrl) ~> addHeader("api_key", wrongVendorKey) ~>
          routes ~> check {
            status === NotFound
            responseAs[String] must
            contain("There are no schemas for this vendor")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(formatUrl) ~> addHeader("api_key", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for name based url" should {

        "return the catalog of available schemas for this name" in {
          Get(nameUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        "return the catalog of available schemas for those names" in {
          Get(multiNameUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return metadata about every schema having this vendor, name" in {
          Get(metaNameUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(vendor) and contain(name)
          }
        }

        "return metadata about every schema having those names" in {
          Get(metaMultiNameUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return a 404 for a vendor/name combination which has no schemas" in {
          Get(otherNameUrl) ~> addHeader("api_key", wrongVendorKey) ~> routes ~>
          check {
            status === NotFound
            responseAs[String] must
            contain("There are no schemas for this vendor, name combinatio")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(nameUrl) ~> addHeader("api_key", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for format based url" should {

        "return the catalog of available schemas for this format" in {
          Get(formatUrl) ~> addHeader("api_key", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        "return the catalog available schemas for those formats" in {
          Get(multiFormatUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(format) and contain(format2)
          }
        }

        """return metadata about every schema having this vendor, name, format
        combination""" in {
          Get(metaFormatUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(name) and
            contain(format)
          }
        }

        "return metadata about every schema those formats" in {
          Get(metaMultiFormatUrl) ~> addHeader("api_key", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(format) and contain(format2)
          }
        }

        """return a 404 for a vendor/name/format combination which has
        no schemas""" in {
          Get(otherFormatUrl) ~> addHeader("api_key", wrongVendorKey) ~>
          routes ~> check {
            status === NotFound
            responseAs[String] must
            contain("There are no schemas for this vendor, name, format ")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(vendorUrl) ~> addHeader("api_key", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }
    }

    "for POST requests" should {

      //should be removed from db before running tests for now
      "return success if the json is passed as form data" in {
        Post(postUrl1, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === Created
            responseAs[String] must contain("Schema added successfully") and
              contain(vendor)
          }
      }

      //should be removed from db before running tests for now
      "return success if the json is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === Created
            responseAs[String] must contain("Schema added successfully") and
              contain(vendor)
          }
      }

      "return a 401 if the schema already exists with form data" in {
        Post(postUrl6) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 401 if the schema already exists with query param" in {
        Post(url, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must contain("This schema already exists")
          }
      }

      "return a 400 if no form or query param is specified" in {
        Post(postUrl3) ~> addHeader("api_key", writeKey) ~>
          sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must
              contain("Request is missing required form field 'json'")
          }
      }

      """return 401 if the API key doesn't have sufficient permissions with
        query param""" in {
          Post(postUrl4) ~> addHeader("api_key", readKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
      }

      """return a 401 if the API key doesn't have sufficient permissions
        with form data""" in {
          Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
            addHeader("api_key", readKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      "return a 401 if no api_key is specified with query param" in {
        Post(postUrl4) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("The resource requires authentication")
        }
      }

      "return a 401 if no api_key is specified with form data" in {
        Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The resource requires authentication")
        }
      }

      "return a 401 if the API key is not an uuid with form data" in {
        Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
          addHeader("api_key", notUuidKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
        }
      }

      "return a 401 if the API key is not an uuid with query param" in {
        Post(postUrl4) ~> addHeader("api_key", notUuidKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("The supplied authentication is invalid")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
        schema's vendor with query param""" in {
          Post(postUrl6) ~> addHeader("api_key", wrongVendorKey) ~>
            sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      """return a 401 if the owner of the API key is not a prefix of the
        schema's vendor with form data""" in {
          Post(postUrl3, FormData(Seq("json" -> validSchema))) ~>
            addHeader("api_key", wrongVendorKey) ~> sealRoute(routes) ~> check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
          }
      }

      """return a 400 if the supplied json is not self-describing with query
      param and contain a validation failure report""" in {
        Post(postUrl7) ~> addHeader("api_key", writeKey) ~> sealRoute(routes) ~>
          check {
            status === BadRequest
            responseAs[String] must
              contain("The json provided is not a valid") and
              contain("report")
          }
      }

      """"return a 400 if the supplied json is not self-describing with form
      data and contain a validation failure report""" in {
        Post(postUrl3, FormData(Seq("json" -> invalidSchema))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must
              contain("The json provided is not a valid") and
              contain("report")
          }
      }

      "return a 400 if the supplied string is not a json with query param" in {
        Post(postUrl8) ~> addHeader("api_key", writeKey) ~> sealRoute(routes) ~>
          check {
            status === BadRequest
            responseAs[String] must contain("The json provided is not valid")
          }
      }

      "return a 400 if the supplied string is not a json with form data" in {
        Post(postUrl3, FormData(Seq("json" -> notJson))) ~>
          addHeader("api_key", writeKey) ~> sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must contain("The json provided is not valid")
          }
      }
    }
  }
}
