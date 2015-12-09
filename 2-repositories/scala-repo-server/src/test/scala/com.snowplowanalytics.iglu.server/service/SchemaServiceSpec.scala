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
package service

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

class SchemaServiceSpec extends Specification
  with Api with Specs2RouteTest with NoTimeConversions with SetupAndDestroy {

  def actorRefFactory = system

  implicit val routeTestTimeout = RouteTestTimeout(20 seconds)

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
  val publicSchemasUrl = s"${start}public"
  val metaPublicSchemasUrl = s"${publicSchemasUrl}?filter=metadata"

  val url = s"${start}${vendor}/${name}/${format}/${version}"
  val publicUrl = s"${start}${otherVendor}/${name}/${format}/${version}"
  val multiUrl = s"${url},${vendor}/${name2}/${format}/${version}"
  val multiPublicUrl = s"${url},${otherVendor}/${name}/${format}/${version}"
  val faultyUrl = s"${start}${vendor}/${name}/jsonchema/${version}"
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
  val multiVendorUrl = s"${vendorUrl},${vendor2}"
  val multiVendorPublicUrl = s"${vendorPublicUrl},${otherVendor2}"
  val metaVendorUrl = s"${vendorUrl}?filter=metadata"
  val metaVendorPublicUrl = s"${vendorPublicUrl}?filter=metadata"
  val metaMultiVendorUrl = s"${multiVendorUrl}?filter=metadata"
  val metaMultiVendorPublicUrl = s"${multiVendorPublicUrl}?filter=metadata"

  val nameUrl = s"${start}${vendor}/${name}"
  val namePublicUrl = s"${start}${otherVendor}/${name}"
  val multiNameUrl = s"${nameUrl},${name2}"
  val multiNamePublicUrl = s"${namePublicUrl},${name2}"
  val metaNameUrl = s"${nameUrl}?filter=metadata"
  val metaNamePublicUrl = s"${namePublicUrl}?filter=metadata"
  val metaMultiNameUrl = s"${multiNameUrl}?filter=metadata"
  val metaMultiNamePublicUrl = s"${multiNamePublicUrl}?filter=metadata"

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

  //post urls
  val postUrl1 = s"${start}${vendor}/unit_test1/${format}/${version}"
  val postUrl2 = s"${start}${vendor}/unit_test2/${format}/${version}" +
    s"?schema=${validSchemaUri}"
  val postUrl3 = s"${start}${vendor}/unit_test3/${format}/${version}"
  val postUrl4 = s"${start}${vendor}/unit_test4/${format}/${version}" +
    s"?schema=${validSchemaUri}"
  val postUrl6 = s"${url}?schema=${validSchemaUri}"
  val postUrl7 = s"${start}${vendor}/unit_test7/${format}/${version}" +
    s"?schema=${invalidSchemaUri}"
  val postUrl8 = s"${start}${vendor}/unit_test8/${format}/${version}" +
    s"?schema=${notJson}"
  val postUrl9 = s"${start}${vendor}/unit_test9/${format}/${version}" +
    s"?isPublic=true"
  val postUrl10 = s"${start}${vendor}/unit_test10/${format}/${version}" +
    s"?schema=${validSchemaUri}&isPublic=true"
  val postUrl11 = s"${start}${vendor}/unit_test11/${format}/${version}"
  val postUrl12 = s"${start}${vendor}/unit_test12/${format}/${version}"

  //put urls
  val putUrl1 = s"${start}${vendor}/unit_test13/${format}/${version}"
  val putUrl2 = s"${start}${vendor}/unit_test14/${format}/${version}" +
    s"?schema=${validSchemaUri}"
  val putUrl3 = s"${start}${vendor}/unit_test15/${format}/${version}"

  sequential

  "SchemaService" should {

    "for GET requests" should {

      "for the /api/schemas/public endpoint" should {

        "return a proper catalog of public schemas" in {
          Get(publicSchemasUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(otherVendor)
          }
        }

        "return proper metadata for every public schema" in {
          Get(metaPublicSchemasUrl) ~> addHeader("apikey", readKey) ~>
          routes ~> check {
            status === OK
            responseAs[String] must contain(otherVendor)
          }
        }
      }

      "for version based urls" should {

        "return a proper json for well-formed single GET requests" +
        s"(${url})" in {
          Get(url) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(name)
          }
        }

        s"return a proper json for a public schema (${publicUrl})" in {
          Get(publicUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(otherVendor)
          }
        }

        // "return a proper json for multi version urls" +
        // s"(${multiVersionUrl})" in {
        //   Get(multiVersionUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(version) and contain(version2)
        //   }
        // }

        // "return a proper json for multi version urls with public schemas" +
        // s"(${multiVersionPublicUrl})" in {
        //   Get(multiVersionPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(version) and contain(version2)
        //   }
        // }

        // s"return a proper json for multi format urls (${multiFormat})" in {
        //   Get(multiFormat) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(format) and contain(format2)
        //   }
        // }

        // "return a proper json for multi format urls with public schemas" +
        // s"(${multiFormatPublic})" in {
        //   Get(multiFormatPublic) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(format) and
        //       contain(format2)
        //   }
        // }

        // s"return a proper json for multi name urls (${multiName})" in {
        //   Get(multiName) ~> addHeader("apikey", readKey) ~> routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(name) and contain(name2)
        //   }
        // }

        // "return a proper json for multi name urls with public schemas" +
        // s"(${multiNamePublic})" in {
        //   Get(multiNamePublic) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(name) and
        //       contain(name2)
        //   }
        // }

        // s"return a proper json for multi vendor urls (${multiVendor})" in {
        //   Get(multiVendor) ~> addHeader("apikey", readKey) ~> routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(vendor) and contain(vendor2)
        //   }
        // }

        // "return a proper json for multi vendor urls with public schemas" +
        // s"(${multiVendorPublic})" in {
        //   Get(multiVendorPublic) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(otherVendor2)
        //   }
        // }

        "return proper metadata for well-formed single GET requests" +
        s"(${metaUrl})" in {
          Get(metaUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(vendor) and contain(name) and
              contain(format) and contain(version)
          }
        }

        s"return proper metadata for a public schema ${metaPublicUrl}" in {
          Get(metaPublicUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(otherVendor) and contain(name) and
              contain(format) and contain(version)
          }
        }

        // "return proper metadata for multi version urls" +
        // s"(${metaMultiVersionUrl})" in {
        //   Get(metaMultiVersionUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(version) and contain(version2)
        //   }
        // }

        // "return proper metadata for multi version urls with public schemas" +
        // s"${metaMultiVersionPublicUrl}" in {
        //   Get(metaMultiVersionPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(version) and contain(version2)
        //   }
        // }

        // "return proper metadata for multi format urls" +
        // s"(${metaMultiFormat})" in {
        //   Get(metaMultiFormat) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(format) and contain(format2)
        //   }
        // }

        // "return proper metadata for multi format urls with public schemas" +
        // s"(${metaMultiFormatPublic})" in {
        //   Get(metaMultiFormatPublic) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(format) and
        //       contain(format2)
        //   }
        // }

        // s"return proper metadata for multi name urls (${metaMultiName})" in {
        //   Get(metaMultiName) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(name) and contain(name2)
        //   }
        // }

        // "return proper metadata for multi name urls with public schemas" +
        // s"(${metaMultiNamePublic})" in {
        //   Get(metaMultiNamePublic) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(name) and
        //       contain(name2)
        //   }
        // }

        // "return proper metadata for multi vendor urls" +
        // s"(${metaMultiVendor})" in {
        //   Get(metaMultiVendor) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(vendor) and contain(vendor2)
        //   }
        // }

        // "return proper metadata for multi vendor urls with public schemas" +
        // s"(${metaMultiVendorPublic})" in {
        //   Get(metaMultiVendorPublic) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(otherVendor2)
        //   }
        // }

        "return a 404 for GET requests for which the schema is not in the db" in
        {
          Get(faultyUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === NotFound
            responseAs[String] must
              contain("There are no schemas available here")
          }
        }

        "return a 401 if no apikey is found in the db" in {
          Get(url) ~> addHeader("apikey", faultyKey) ~> sealRoute(routes) ~>
            check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
        }

        "return a 401 if the API key provided is not an uuid" in {
          Get(url) ~> addHeader("apikey", notUuidKey) ~> sealRoute(routes) ~>
            check {
              status === Unauthorized
              responseAs[String] must
                contain("You do not have sufficient privileges")
            }
        }

        "return a 401 if no apikey is provided" in {
          Get(url) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("You do not have sufficient privileges")
          }
        }

        """return a 401 if the owner of the API key is not a prefix of the
          schema's vendor""" in {
            Get(url) ~> addHeader("apikey", wrongVendorKey) ~>
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

      "for vendor based urls" should {

        "return the catalog of available schemas for this vendor" +
        s"(${vendorUrl})" in {
          Get(vendorUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return the catalog of available public schemas for another vendor" +
        s"(${vendorPublicUrl})" in {
          Get(vendorPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(otherVendor) and contain(name) and
              contain(name2)
          }
        }

        "return the catalog of available schemas for those vendors" +
        s"(${multiVendorUrl})" in {
          Get(multiVendorUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        // "return the catalog of available public schemas for other vendors" +
        // s"(${multiVendorPublicUrl})" in {
        //   Get(multiVendorPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(otherVendor2)
        //   }
        // }

        "return metadata about every schema for this vendor" +
        s"(${metaVendorUrl})" in {
          Get(metaVendorUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor)
          }
        }

        "return metadata about every public schema for another vendor" +
        s"(${metaVendorPublicUrl})" in {
          Get(metaVendorPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(otherVendor)
          }
        }

        "return metadata about every schema for those vendors" +
        s"(${metaMultiVendorUrl})" in {
          Get(metaMultiVendorUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(vendor) and contain(vendor2)
          }
        }

        // "return metadata about every public schema for other vendors" +
        // s"(${metaMultiVendorPublicUrl})" in {
        //   Get(metaMultiVendorPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(otherVendor2)
        //   }
        // }

        "return a 404 for a vendor which has no schemas" in {
          Get(otherVendorUrl) ~> addHeader("apikey", wrongVendorKey) ~>
          routes ~> check {
            status === NotFound
            responseAs[String] must
            contain("There are no schemas for this vendor")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(vendorUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for name based urls" should {

        "return the catalog of available schemas for this name" +
        s"(${nameUrl})" in {
          Get(nameUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        // "return the catalog of available public schemas for this name" +
        // s"(${namePublicUrl})" in {
        //   Get(namePublicUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(version) and contain(version2)
        //   }
        // }

        "return the catalog of available schemas for those names" +
        s"(${multiNameUrl})" in {
          Get(multiNameUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return the catalog of available public schemas for those names" +
        s"(${multiNamePublicUrl})" in {
          Get(multiNamePublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(otherVendor) and contain(name) and
              contain(name2)
          }
        }

        "return metadata about every schema having this vendor, name" +
        s"(${metaNameUrl})" in {
          Get(metaNameUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(vendor) and contain(name)
          }
        }

        // "return metadata about every public schema having this vendor, name" +
        // s"(${metaNamePublicUrl})" in {
        //   Get(metaNamePublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(name)
        //   }
        // }

        "return metadata about every schema having those names" +
        s"(${metaMultiNameUrl})" in {
          Get(metaMultiNameUrl) ~> addHeader("apikey", readKey) ~> routes ~>
          check {
            status === OK
            responseAs[String] must contain(name) and contain(name2)
          }
        }

        "return metadata about every public schema having those names" +
        s"(${metaMultiNamePublicUrl})" in {
          Get(metaMultiNamePublicUrl) ~> addHeader("apikey", readKey) ~>
          routes ~> check {
            status === OK
            responseAs[String] must contain(otherVendor) and contain(name) and
              contain(name2)
          }
        }

        "return a 404 for a vendor/name combination which has no schemas" in {
          Get(otherNameUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
          check {
            status === NotFound
            responseAs[String] must
              contain("There are no schemas for this vendor, name combination")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(nameUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }

      "for format based urls" should {

        "return the catalog of available schemas for this format" +
        s"(${formatUrl})" in {
          Get(formatUrl) ~> addHeader("apikey", readKey) ~> routes ~> check {
            status === OK
            responseAs[String] must contain(version) and contain(version2)
          }
        }

        // "return the catalog of available public schemas for this format" +
        // s"(${formatPublicUrl})" in {
        //   Get(formatPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and
        //       contain(version) and contain(version2)
        //   }
        // }

        // "return the catalog of available schemas for those formats" +
        // s"(${multiFormatUrl})" in {
        //   Get(multiFormatUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(format) and contain(format2)
        //   }
        // }

        // "return the catalog of available public schemas for those formats" +
        // s"(${multiFormatPublicUrl})" in {
        //   Get(multiFormatPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(format) and
        //       contain(format2)
        //   }
        // }

        // "return metadata about every schema having this vendor, name, format" +
        // s"combination (${metaFormatUrl})" in {
        //   Get(metaFormatUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(vendor) and contain(name) and
        //       contain(format)
        //   }
        // }

        // "return metadata about every public schema having this other vendor," +
        // s" name, format (${metaFormatPublicUrl})" in {
        //   Get(metaFormatPublicUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(name) and
        //       contain(format)
        //   }
        // }

        // "return metadata about every schema having those formats" +
        // s"(${metaMultiFormatUrl})" in {
        //   Get(metaMultiFormatUrl) ~> addHeader("apikey", readKey) ~> routes ~>
        //   check {
        //     status === OK
        //     responseAs[String] must contain(format) and contain(format2)
        //   }
        // }

        // "return metadata about every public schema having those formats" +
        // s"(${metaMultiFormatPublicUrl})" in {
        //   Get(metaMultiFormatPublicUrl) ~> addHeader("apikey", readKey) ~>
        //   routes ~> check {
        //     status === OK
        //     responseAs[String] must contain(otherVendor) and contain(format) and
        //       contain(format2)
        //   }
        // }

        """return a 404 for a vendor/name/format combination which has
        no schemas""" in {
          Get(otherFormatUrl) ~> addHeader("apikey", wrongVendorKey) ~>
          routes ~> check {
            status === NotFound
            responseAs[String] must
            contain("There are no schemas for this vendor, name, format ")
          }
        }

        "return a 401 if the owner is not a prefix of the vendor" in {
          Get(formatUrl) ~> addHeader("apikey", wrongVendorKey) ~> routes ~>
          check {
            status === Unauthorized
            responseAs[String] must contain("You do not have sufficient privil")
          }
        }
      }
    }

    "for POST requests" should {

      //should be removed from db before running tests for now
      "return success if the schema is passed as form data" in {
        Post(postUrl1, FormData(Seq("schema" -> validSchema))) ~>
          addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
            status === Created
            responseAs[String] must contain("Schema successfully added") and
              contain(vendor)
          }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as query parameter" in {
        Post(postUrl2) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
            status === Created
            responseAs[String] must contain("Schema successfully added") and
              contain(vendor)
          }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as request body" in {
        Post(postUrl11, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as form data and is public" in {
        Post(postUrl9, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      "return success if the schema is passed as query param and is public" in {
        Post(postUrl10) ~> addHeader("apikey", writeKey) ~>
        sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      //should be removed from db before running tests for now
      """return success if the schemas is passed as request body and is
      public""" in {
        Post(postUrl12, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      // "return a 401 if the schema already exists with form data" in {
      //   Post(postUrl6) ~> addHeader("apikey", writeKey) ~>
      //     sealRoute(routes) ~> check {
      //       status === Unauthorized
      //       responseAs[String] must contain("This schema already exists")
      //     }
      // }

      // "return a 401 if the schema already exists with query param" in {
      //   Post(url, FormData(Seq("schema" -> validSchema))) ~>
      //     addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
      //       status === Unauthorized
      //       responseAs[String] must contain("This schema already exists")
      //     }
      // }

      // "return a 401 if the schema already exists with body request" in {
      //   Post(postUrl12, HttpEntity(`application/json`, validSchema)) ~>
      //   addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
      //     status === Unauthorized
      //     responseAs[String] must contain("This schema already exists")
      //   }
      // }

      """return a 400 if no form data or query param or body request is
      specified""" in {
        Post(postUrl3) ~> addHeader("apikey", writeKey) ~>
          sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must
              contain("The schema provided is not valid")
          }
      }

      """return 401 if the API key doesn't have sufficient permissions with
      query param""" in {
        Post(postUrl4) ~> addHeader("apikey", readKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("You do not have sufficient privileges")
          }
      }

      """return a 401 if the API key doesn't have sufficient permissions
      with form data""" in {
        Post(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
          addHeader("apikey", readKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions with
      body request""" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", readKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with query param" in {
        Post(postUrl4) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with form data" in {
        Post(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with body request" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with query param" in {
        Post(postUrl4) ~> addHeader("apikey", notUuidKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with form data" in {
        Post(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with body request" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with query param""" in {
        Post(postUrl6) ~> addHeader("apikey", wrongVendorKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with form data""" in {
        Post(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", wrongVendorKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with body request""" in {
        Post(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", wrongVendorKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 400 if the supplied schema is not self-describing with query
      param and contain a validation failure report""" in {
        Post(postUrl7) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      """return a 400 if the supplied schema is not self-describing with form
      data and contain a validation failure report""" in {
        Post(postUrl3, FormData(Seq("schema" -> invalidSchema))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      """return a 400 if the supplied schema is not self-describing with body
      request and contain a validation failure report""" in {
        Post(postUrl3, HttpEntity(`application/json`, invalidSchema)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      "return a 400 if the supplied string is not a schema with query param" in
      {
        Post(postUrl8) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      "return a 400 if the supplied string is not a schema with form data" in {
        Post(postUrl3, FormData(Seq("schema" -> notJson))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      """return a 400 if the supplied string is not a schema with body
      request""" in {
        Post(postUrl3, HttpEntity(`application/json`, notJson)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }
    }

    "for PUT requests" should {

      // "return a 200 if the schema already exists with form data" in {
      //   Put(postUrl1, FormData(Seq("schema" -> validSchema))) ~>
      //   addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
      //     status === OK
      //     responseAs[String] must contain("Schema successfully updated") and
      //       contain(vendor)
      //   }
      // }

      // "return a 200 if the schema already exists with query param" in {
      //   Put(postUrl2) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
      //   check {
      //     status === OK
      //     responseAs[String] must contain("Schema successfully updated") and
      //       contain(vendor)
      //   }
      // }

      // "return a 200 if the schema already exists with request body" in {
      //   Put(postUrl11, HttpEntity(`application/json`, validSchema)) ~>
      //   addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
      //     status === OK
      //     responseAs[String] must contain("Schema successfully updated") and
      //       contain(vendor)
      //   }
      // }

      "return a 201 if the schema doesnt already exist with form data" in {
        Put(putUrl1, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      "return a 201 if the schema doesnt already exist with query param" in {
        Put(putUrl2) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      "return a 201 if the schema doesnt already exist with body request" in {
        Put(putUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === Created
          responseAs[String] must contain("Schema successfully added") and
            contain(vendor)
        }
      }

      """return a 400 if no form data or query param or body request is
      specified""" in {
        Put(postUrl3) ~> addHeader("apikey", writeKey) ~>
          sealRoute(routes) ~> check {
            status === BadRequest
            responseAs[String] must
              contain("The schema provided is not valid")
          }
      }

      """return 401 if the API key doesn't have sufficient permissions with
      query param""" in {
        Put(postUrl4) ~> addHeader("apikey", readKey) ~>
          sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("You do not have sufficient privileges")
          }
      }

      """return a 401 if the API key doesn't have sufficient permissions
      with form data""" in {
        Put(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
          addHeader("apikey", readKey) ~> sealRoute(routes) ~> check {
            status === Unauthorized
            responseAs[String] must
              contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the API key doesn't have sufficient permissions with
      body request""" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", readKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with query param" in {
        Put(postUrl4) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with form data" in {
        Put(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if no apikey is specified with body request" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with query param" in {
        Put(postUrl4) ~> addHeader("apikey", notUuidKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with form data" in {
        Put(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the API key is not an uuid with body request" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", notUuidKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with query param""" in {
        Put(postUrl6) ~> addHeader("apikey", wrongVendorKey) ~>
        sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with form data""" in {
        Put(postUrl3, FormData(Seq("schema" -> validSchema))) ~>
        addHeader("apikey", wrongVendorKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 401 if the owner of the API key is not a prefix of the
      schema's vendor with body request""" in {
        Put(postUrl3, HttpEntity(`application/json`, validSchema)) ~>
        addHeader("apikey", wrongVendorKey) ~> sealRoute(routes) ~> check {
          status === Unauthorized
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 400 if the supplied schema is not self-describing with query
      param and contain a validation failure report""" in {
        Put(postUrl7) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      """return a 400 if the supplied schema is not self-describing with form
      data and contain a validation failure report""" in {
        Put(postUrl3, FormData(Seq("schema" -> invalidSchema))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      """return a 400 if the supplied schema is not self-describing with body
      request and contain a validation failure report""" in {
        Put(postUrl3, HttpEntity(`application/json`, invalidSchema)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must
            contain("The schema provided is not a valid self-describing") and
            contain("report")
        }
      }

      "return a 400 if the supplied string is not a schema with query param" in
      {
        Put(postUrl8) ~> addHeader("apikey", writeKey) ~> sealRoute(routes) ~>
        check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      "return a 400 if the supplied string is not a schema with form data" in {
        Put(postUrl3, FormData(Seq("schema" -> notJson))) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }

      """return a 400 if the supplied string is not a schema with body
      request""" in {
        Put(postUrl3, HttpEntity(`application/json`, notJson)) ~>
        addHeader("apikey", writeKey) ~> sealRoute(routes) ~> check {
          status === BadRequest
          responseAs[String] must contain("The schema provided is not valid")
        }
      }
    }
  }
}
