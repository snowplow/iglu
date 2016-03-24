/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.iglu.core

// specs2
import org.specs2.Specification

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

class ExtractFromSpec extends Specification { def is = s2"""
  Specification ExtractFrom type class for instances
    extract SchemaKey using postfix method $e1
    extract SchemaKey using unsafe postfix method $e2
    extract SchemaKey using AttachTo type class $e3
    throw exception on calling unsafe method $e4
    fail to extract SchemaKey with invalid SchemaVer $e7

  Specification ExtractFrom type class for Schemas
    extract SchemaKey using postfix method $e5
    fail to extract SchemaKey with invalid SchemaVer $e6
  """

  def e1 = {
    import IgluCoreCommon.Json4sExtractFromData

    val json: JValue = parse(
      """
        |{
        |  "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
        |  "data": null
        |}
      """.stripMargin)

    json.getSchemaKey must beSome(
      SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer(2,0,3))
    )
  }

  def e2 = {
    import IgluCoreCommon.Json4sExtractFromData

    val json: JValue = parse(
      """
        |{
        |  "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
        |  "data": null
        |}
      """.stripMargin)

    json.getSchemaKeyUnsafe must beEqualTo(SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer(2,0,3)))
  }

  def e3 = {
    import IgluCoreCommon.Json4sAttachToData

    val json: JValue = parse(
      """
        |{
        |  "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
        |  "data": null
        |}
      """.stripMargin)

    json.getSchemaKey must beSome(
      SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer(2,0,3))
    )
  }

  def e4 = {
    import IgluCoreCommon.Json4sExtractFromData

    val json: JValue = parse(
      """
        |{ "data": null }
      """.stripMargin)

    json.getSchemaKeyUnsafe must throwA[RuntimeException].like {
      case e => e.getMessage must beEqualTo("Cannot extract SchemaKey from object [JObject(List((data,JNull)))]")
    }
  }

  def e5 = {
    import IgluCoreCommon.Json4sExtractFromSchema

    val json: JValue = parse(
      """
        |{
        |	"self": {
        |		"vendor": "com.acme",
        |		"name": "keyvalue",
        |		"format": "jsonschema",
        |		"version": "1-1-0"
        |	},
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        |	}
        |}
      """.stripMargin)

    json.getSchemaKey must beSome(
      SchemaKey("com.acme", "keyvalue", "jsonschema", SchemaVer(1,1,0))
    )
  }

  def e6 = {
    import IgluCoreCommon.Json4sExtractFromSchema

    // SchemaVer cannot have 0 as MODEL
    val json: JValue = parse(
      """
        |{
        |	"self": {
        |		"vendor": "com.acme",
        |		"name": "keyvalue",
        |		"format": "jsonschema",
        |		"version": "0-1-0"
        |	},
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        |	}
        |}
      """.stripMargin)

    json.getSchemaKey must beNone
  }

  def e7 = {
    import IgluCoreCommon.Json4sExtractFromData

    // SchemaVer cannot have preceding 0 in REVISION
    val json: JValue = parse(
      """
        |{
        |  "schema": "iglu:com.acme.useless/null/jsonschema/2-01-3",
        |  "data": null
        |}
      """.stripMargin)

    json.getSchemaKey must beNone
  }
}
