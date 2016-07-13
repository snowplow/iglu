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
import java.io.File

// scalaz
import scalaz.Success

// json4s
import org.json4s.jackson.JsonMethods.parse

// Iglu
import com.snowplowanalytics.iglu.core.{ SchemaKey, SchemaVer }
import com.snowplowanalytics.iglu.core.Containers.SelfDescribingSchema

// specs2
import org.specs2.Specification

class FileUtilsSpec extends Specification { def is = s2"""
  Check File utils
    correctly construct file path for TextFile $e1
    correctly extract SchemaKey from JSON file with JSON Schema $e2
  """

  def e1 = {
    val file = FileUtils.TextFile("some_file.sql", "somecontent")
    val endFile = file.setBasePath("root1").setBasePath("root2").setBasePath("root3")

    val f = new File("root3/root2/root1", "some_file.sql")
    endFile must beEqualTo(FileUtils.TextFile(f, "somecontent"))
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

    val schemaFile = FileUtils.JsonFile(None, "1-0-0", schema)

    // result
    val schemaKey = SchemaKey("org.ietf", "http_cookie", "jsonschema", SchemaVer(1,0,0))
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


    schemaFile.extractSelfDescribingSchema must beEqualTo(
      Success(SelfDescribingSchema(schemaKey, body))
    )
  }

}
