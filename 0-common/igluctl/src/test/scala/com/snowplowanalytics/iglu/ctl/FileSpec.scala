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
package com.snowplowanalytics.iglu.ctl

// java
import java.nio.file.Paths

// cats
import cats.syntax.either._

// json4s
import org.json4s.jackson.JsonMethods.parse

// Iglu
import com.snowplowanalytics.iglu.core.{ SchemaMap, SchemaVer, SelfDescribingSchema }
import com.snowplowanalytics.iglu.ctl.Common.Error

// specs2
import org.specs2.Specification

class FileSpec extends Specification { def is = s2"""
  Check File utils
    correctly construct file path for TextFile $e1
    extractSelfDescribingSchema extracts SchemaMap from JSON file with JSON Schema $e2
    extractSelfDescribingSchema extracts SchemaMap from JSON file with JSON Schema and additional path $e3
    extractSelfDescribingSchema fails to extract SchemaMap from JSON file with JSON Schema and invalid path $e4
  """

  def e1 = {
    val file = File.textFile(Paths.get("some_file.sql"), "somecontent")
    val endFile = file.setBasePath("root1").setBasePath("root2").setBasePath("root3")

    val f = Paths.get("root3/root2/root1", "some_file.sql")
    endFile must beTypedEqualTo(File.textFile(f, "somecontent"))
  }

  // TODO: see https://github.com/snowplow/iglu/issues/165
  def e2 = {
    val schema = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "org.ietf",
         |		"name": "http_cookie",
         |		"format": "jsonschema",
         |		"version": "1-0-0"
         |	},
         |
         |	"type": "object",
         |	"properties": {
         |		"name": {
         |			"type": "string",
         |			"maxLength" : 4096
         |		},
         |		"value": {
         |			"type": ["string", "null"],
         |			"maxLength" : 4096
         |		}
         |	},
         |	"required": ["name", "value"],
         |	"additionalProperties": false
         |}""".stripMargin)

    val schemaFile = File.jsonFile(Paths.get("org.ietf/http_cookie/jsonschema/1-0-0"), schema)

    // result
    val schemaKey = SchemaMap("org.ietf", "http_cookie", "jsonschema", SchemaVer.Full(1,0,0))
    val body = parse(
      """
        |{
        |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
        |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
        |	"type": "object",
        |	"properties": {
        |		"name": {
        |			"type": "string",
        |			"maxLength" : 4096
        |		},
        |		"value": {
        |			"type": ["string", "null"],
        |			"maxLength" : 4096
        |		}
        |	},
        |	"required": ["name", "value"],
        |	"additionalProperties": false
        |}
      """.stripMargin)


    schemaFile.asSchema.map(_.content) must beRight(SelfDescribingSchema(schemaKey, body))
  }

  def e3 = {
    val schema = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "com.acme",
         |		"name": "example",
         |		"format": "jsonschema",
         |		"version": "1-0-0"
         |	},
         |	"properties": {
         |		"name": {}
         |	}
         |}""".stripMargin)
    val schemaFile = File.jsonFile(Paths.get("/additional/path/com.acme/example/jsonschema/1-0-0"), schema)

    // result
    val schemaMap = SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,0))
    val body = parse(
      """
        |{
        |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
        |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
        |	"properties": {
        |		"name": {}
        |	}
        |}
      """.stripMargin)

    val contentExpectation = schemaFile.asSchema.map(_.content) must beRight(SelfDescribingSchema(schemaMap, body))
    val pathExpectation = schemaFile.asSchema.map(_.path) must beRight(Paths.get("/additional/path/com.acme/example/jsonschema/1-0-0"))
    pathExpectation and contentExpectation
  }

  def e4 = {
    val schema = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "com.acme",
         |		"name": "example",
         |		"format": "jsonschema",
         |		"version": "1-0-0"
         |	},
         |	"properties": {
         |		"name": {}
         |	}
         |}""".stripMargin)
    val schemaFile = File.jsonFile(Paths.get("/com.failure/example/jsonschema/1-0-0"), schema)
    val expected = Error.PathMismatch(Paths.get("/com.failure/example/jsonschema/1-0-0"),SchemaMap("com.acme","example","jsonschema",SchemaVer.Full(1,0,0)))

    schemaFile.asSchema must beLeft(expected)
  }
}
