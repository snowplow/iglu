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
package com.snowplowanalytics.iglu.core.json4s

// specs2
import org.specs2.Specification

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// This library
import com.snowplowanalytics.iglu.core._

class AttachToSpec extends Specification { def is = s2"""
  Specification AttachTo type class for instances
    add Schema reference to json4s data instance $e1
    add description to json4s Schema $e2
    add and extract SchemaKey to Json $e3
  """

  def e1 = {

    implicit val attachSchemaKey = AttachToData

    val data: JValue = parse(
      """
        |{
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        |}
      """.stripMargin)

    val expected: JValue = parse(
      """
        |{
        | "schema": "iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0",
        | "data": {
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        | }
        |}
      """.stripMargin)

    val result = data.attachSchemaKey(SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0)))
    result must beEqualTo(expected)
  }

  def e2 = {

    implicit val attachSchemaKey = AttachToSchema

    val schema: JValue = parse(
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
      """.stripMargin
    )

    val expected: JValue = parse(
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
      """.stripMargin
    )

    val result = schema.attachSchemaKey(SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0)))
    result must beEqualTo(expected)
  }

  def e3 = {

    implicit val attachSchemaKey = AttachToSchema

    val schema: JValue = parse(
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
      """.stripMargin)

    val key = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0))
    val result = schema.attachSchemaKey(key)
    result.getSchemaKey must beSome(key)
  }
}
