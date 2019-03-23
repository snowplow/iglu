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
import cats.data.{Ior, NonEmptyList}

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
    extractResultFromJsonSchemas returns 'path is empty' error when given json schema list is empty $e5
    extractResultFromJsonSchemas returns 'no valid JSON schemas' error with other given errors when all the items in the given json schema list are error $e6
    extractResultFromJsonSchemas returns both 'gap' error and given schemas  $e7
    extractResultFromJsonSchemas returns both given errors and schemas $e8
    extractResultFromJsonSchemas returns only given schemas when there is no error in given list $e9
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

  def e5 = {
    val path = Paths.get("mock/path")
    val expected = Ior.left(NonEmptyList.of(Error.ReadError(path, "is empty")))
    File.extractResultFromJsonSchemas(Nil, path) must beEqualTo(expected)
  }

  def e6 = {
    val path = Paths.get("mock/path")
    val mockError1 = Error.ReadError(path, "mockError1")
    val mockError2 = Error.ReadError(path, "mockError2")

    val jsonSchemas = List(
      Either.left(mockError1),
      Either.left(mockError2)
    )

    val noValidSchemasError = Error.ReadError(path,"no valid JSON Schemas")
    val expected = Ior.left(
      NonEmptyList.of(
        mockError1,
        mockError2,
        noValidSchemasError
      )
    )

    File.extractResultFromJsonSchemas(jsonSchemas, path) must beEqualTo(expected)
  }

  def e7 = {
    val mainFolderPath = Paths.get("/additional/path/com.acme/example/jsonschema")
    val schemaMap = SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,1))
    val schemaJson = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "com.acme",
         |		"name": "example",
         |		"format": "jsonschema",
         |		"version": "1-0-1"
         |	},
         |	"properties": {
         |		"name": {}
         |	}
         |}""".stripMargin)
    val path = Paths.get("/additional/path/com.acme/example/jsonschema/1-0-1")
    val schema = File.jsonFile(path, schemaJson).asSchema

    val jsonSchemas = List(schema)
    val nonConsistencyError = Error.ConsistencyError(Common.GapError.NotInitSingle(schemaMap))

    val expected = Ior.both(
      NonEmptyList.of(nonConsistencyError),
      NonEmptyList.of(schema.right.get)
    )

    File.extractResultFromJsonSchemas(jsonSchemas, mainFolderPath) must beEqualTo(expected)
  }

  def e8 = {
    val mainFolderPath = Paths.get("/additional/path/com.acme/example/jsonschema")
    val schemaJson = parse(
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
    val path = Paths.get("/additional/path/com.acme/example/jsonschema/1-0-0")
    val schema = File.jsonFile(path, schemaJson).asSchema

    val mockError1 = Error.ReadError(path, "mockError1")
    val mockError2 = Error.ReadError(path, "mockError2")

    val jsonSchemas = List(
      Either.left(mockError1),
      Either.left(mockError2),
      schema
    )

    val expected = Ior.both(
      NonEmptyList.of(mockError1, mockError2),
      NonEmptyList.of(schema.right.get)
    )

    File.extractResultFromJsonSchemas(jsonSchemas, mainFolderPath) must beEqualTo(expected)
  }

  def e9 = {
    val mainFolderPath = Paths.get("/additional/path/com.acme/example/jsonschema")
    val schemaJson1 = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "com.acme",
         |		"name": "example1",
         |		"format": "jsonschema",
         |		"version": "1-0-0"
         |	},
         |	"properties": {
         |		"name": {}
         |	}
         |}""".stripMargin)
    val path1 = Paths.get("/additional/path/com.acme/example1/jsonschema/1-0-0")
    val schema1 = File.jsonFile(path1, schemaJson1).asSchema

    val schemaJson2 = parse(
      """|{
         |	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
         |	"description": "Schema for a single HTTP cookie, as defined in RFC 6265",
         |	"self": {
         |		"vendor": "com.acme",
         |		"name": "example2",
         |		"format": "jsonschema",
         |		"version": "1-0-0"
         |	},
         |	"properties": {
         |		"name": {}
         |	}
         |}""".stripMargin)
    val path2 = Paths.get("/additional/path/com.acme/example2/jsonschema/1-0-0")
    val schema2 = File.jsonFile(path2, schemaJson2).asSchema

    val jsonSchemas = List(schema1, schema2)

    val expected = Ior.right(
      NonEmptyList.of(
        schema1.right.get,
        schema2.right.get
      )
    )

    File.extractResultFromJsonSchemas(jsonSchemas, mainFolderPath) must beEqualTo(expected)
  }
}
