/*
 * Copyright (c) 2012-2017 Snowplow Analytics Ltd. All rights reserved.
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

// specs2
import com.snowplowanalytics.iglu.core.typeclasses.NormalizeSchema
import org.specs2.Specification

// cats (for Scala 2.11)
import cats.syntax.either._

// Circe
import io.circe._
import io.circe.literal._

// This library
import com.snowplowanalytics.iglu.core._

class AttachSchemaKeySpec extends Specification { def is = s2"""
  Specification AttachTo type class for instances
    add Schema reference to circe data instance $e1
    add description to circe Schema $e2
    add and extract SchemaKey to Json $e3
  """

  import implicits._

  def e1 = {

    val data: Json =
      json"""
        {
          "latitude": 32.2,
          "longitude": 53.23,
          "speed": 40
        }
      """

    val expected: Json =
      json"""
        {
         "schema": "iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0",
         "data": {
          "latitude": 32.2,
          "longitude": 53.23,
          "speed": 40
         }
        }
      """

    val result = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer.Full(1,1,0)).attachTo(data)
    result must beEqualTo(expected)
  }

  def e2 = {

    val schema: Json =
      json"""
        {
          "type": "object",
          	"properties": {
          		"latitude": {
          			"type": "number"
          		},
          		"longitude": {
          			"type": "number"
          		},
          		"latitudeLongitudeAccuracy": {
          			"type": ["number", "null"]
          		},
          		"altitude": {
          			"type": ["number", "null"]
          		},
          		"altitudeAccuracy": {
          			"type": ["number", "null"]
          		},
          		"bearing": {
          			"type": ["number", "null"]
          		},
          		"speed": {
          			"type": ["number", "null"]
          		},
          		"timestamp": {
          			"type": ["integer", "null"]
          		}
          	},
          	"required": ["latitude", "longitude"],
          	"additionalProperties": false
        }
      """

    val expected: Json =
      json"""
        {
        	"self": {
        		"vendor": "com.snowplowanalytics.snowplow",
        		"name": "geolocation_context",
        		"format": "jsonschema",
        		"version": "1-1-0"
        	},

        	"type": "object",
        	"properties": {
        		"latitude": {
        			"type": "number"
        		},
        		"longitude": {
        			"type": "number"
        		},
        		"latitudeLongitudeAccuracy": {
        			"type": ["number", "null"]
        		},
        		"altitude": {
        			"type": ["number", "null"]
        		},
        		"altitudeAccuracy": {
        			"type": ["number", "null"]
        		},
        		"bearing": {
        			"type": ["number", "null"]
        		},
        		"speed": {
        			"type": ["number", "null"]
        		},
        		"timestamp": {
        			"type": ["integer", "null"]
        		}
        	},
        	"required": ["latitude", "longitude"],
        	"additionalProperties": false
        }
      """

    val map = SchemaMap("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer.Full(1,1,0))
    val result = implicitly[NormalizeSchema[Json]].normalize(SelfDescribingSchema(map, schema))
    result must beJson(expected)
  }

  def e3 = {
    val schema: Json =
      json"""
        {
          "type": "object",
          	"properties": {
          		"latitude": {
          			"type": "number"
          		},
          		"longitude": {
          			"type": "number"
          		}
        }}
      """

    val key = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer.Full(1,1,0))
    val result = key.attachTo(schema)
    SchemaKey.extract(result) must beRight(key)
  }

  import org.specs2.matcher.Matcher

  def beJson(expected: Json): Matcher[Json] = { actual: Json =>
    (actual == expected, "actual:\n" + actual.spaces2 + "\n" + "expected:\n" + expected.spaces2 + "\n")
  }
}
