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
package com.snowplowanalytics.iglu.core.circe

import cats.syntax.either._

// specs2
import org.specs2.Specification

// Circe
import io.circe._
import io.circe.parser.parse

// This library
import com.snowplowanalytics.iglu.core._

class AttachToSpec extends Specification { def is = s2"""
  Specification AttachTo type class for instances
    add Schema reference to circe data instance $e1
    add description to json4s Schema $e2
    add and extract SchemaKey to Json $e3
  """

  def e1 = {

    implicit val attachSchemaKey = AttachToData

    val data: Json = parse(
      """
        |{
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        |}
      """.stripMargin).toOption.get

    val expected: Json = parse(
      """
        |{
        | "schema": "iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0",
        | "data": {
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        | }
        |}
      """.stripMargin).getOrElse(Json.Null)

    val result = data.attachSchemaKey(SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0)))
    result must beEqualTo(expected)
  }

  def e2 = {

    implicit val attachSchemaKey = AttachToSchema

    val schema: Json = parse(
      """
        |{
        |  "type": "object",
        |  	"properties": {
        |  		"latitude": {
        |  			"type": "number"
        |  		},
        |  		"longitude": {
        |  			"type": "number"
        |  		},
        |  		"latitudeLongitudeAccuracy": {
        |  			"type": ["number", "null"]
        |  		},
        |  		"altitude": {
        |  			"type": ["number", "null"]
        |  		},
        |  		"altitudeAccuracy": {
        |  			"type": ["number", "null"]
        |  		},
        |  		"bearing": {
        |  			"type": ["number", "null"]
        |  		},
        |  		"speed": {
        |  			"type": ["number", "null"]
        |  		},
        |  		"timestamp": {
        |  			"type": ["integer", "null"]
        |  		}
        |  	},
        |  	"required": ["latitude", "longitude"],
        |  	"additionalProperties": false
        |}
      """.stripMargin).getOrElse(Json.Null)

    val expected: Json = parse(
      """
        |{
        |	"self": {
        |		"vendor": "com.snowplowanalytics.snowplow",
        |		"name": "geolocation_context",
        |		"format": "jsonschema",
        |		"version": "1-1-0"
        |	},
        |
        |	"type": "object",
        |	"properties": {
        |		"latitude": {
        |			"type": "number"
        |		},
        |		"longitude": {
        |			"type": "number"
        |		},
        |		"latitudeLongitudeAccuracy": {
        |			"type": ["number", "null"]
        |		},
        |		"altitude": {
        |			"type": ["number", "null"]
        |		},
        |		"altitudeAccuracy": {
        |			"type": ["number", "null"]
        |		},
        |		"bearing": {
        |			"type": ["number", "null"]
        |		},
        |		"speed": {
        |			"type": ["number", "null"]
        |		},
        |		"timestamp": {
        |			"type": ["integer", "null"]
        |		}
        |	},
        |	"required": ["latitude", "longitude"],
        |	"additionalProperties": false
        |}
      """.stripMargin).getOrElse(Json.Null)

    val result = schema.attachSchemaKey(SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0)))
    result must beJson(expected)
  }

  def e3 = {

    implicit val attachSchemaKey = AttachToSchema

    val schema: Json = parse(
      """
        |{
        |  "type": "object",
        |  	"properties": {
        |  		"latitude": {
        |  			"type": "number"
        |  		},
        |  		"longitude": {
        |  			"type": "number"
        |  		}
        |}}
      """.stripMargin).getOrElse(Json.Null)

    val key = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0))
    val result = schema.attachSchemaKey(key)
    result.getSchemaKey must beSome(key)
  }

  import org.specs2.matcher.Matcher

  def beJson(expected: Json): Matcher[Json] = { actual: Json =>
    (actual == expected, "actual:\n" + actual.spaces2 + "\n" + "expected:\n" + expected.spaces2 + "\n")
  }
}
