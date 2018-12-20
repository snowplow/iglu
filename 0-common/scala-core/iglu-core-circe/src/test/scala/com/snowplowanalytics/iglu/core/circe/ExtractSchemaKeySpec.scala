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
import org.specs2.Specification

import cats.syntax.either._

// circe
import io.circe._
import io.circe.literal._

// This library
import com.snowplowanalytics.iglu.core._

class ExtractSchemaKeySpec extends Specification { def is = s2"""
  Specification ExtractFrom type class for instances
    extract SchemaKey using postfix method $e1
    extract SchemaKey using AttachTo type class $e3
    fail to extract SchemaKey with invalid SchemaVer $e7

  Specification ExtractFrom type class for Schemas
    extract SchemaMap $e5
    fail to extract SchemaMap with invalid SchemaVer $e6
  """

  import implicits._

  def e1 = {

    val json: Json =
      json"""
        {
          "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
          "data": null
        }
      """

    SchemaKey.extract(json) must beRight(
      SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer.Full(2,0,3))
    )
  }

  def e3 = {

    val json: Json =
      json"""
        {
          "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
          "data": null
        }
      """

    SchemaKey.extract(json) must beRight(
      SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer.Full(2,0,3))
    )
  }

  def e5 = {

    val json: Json =
      json"""
        {
        	"self": {
        		"vendor": "com.acme",
        		"name": "keyvalue",
        		"format": "jsonschema",
        		"version": "1-1-0"
        	},
        	"type": "object",
        	"properties": {
        		"name": { "type": "string" },
        		"value": { "type": "string" }
        	}
        }
      """

    SchemaMap.extract(json) must beRight(
      SchemaMap("com.acme", "keyvalue", "jsonschema", SchemaVer.Full(1,1,0))
    )
  }

  def e6 = {

    // SchemaVer cannot have 0 as MODEL
    val json: Json =
      json"""
        {
        	"self": {
        		"vendor": "com.acme",
        		"name": "keyvalue",
        		"format": "jsonschema",
        		"version": "0-1-0"
        	},
        	"type": "object",
        	"properties": {
        		"name": { "type": "string" },
        		"value": { "type": "string" }
        	}
        }
      """

    SchemaMap.extract(json) must beLeft(ParseError.InvalidSchemaVer)
  }

  def e7 = {

    // SchemaVer cannot have preceding 0 in REVISION
    val json: Json =
      json"""
        {
          "schema": "iglu:com.acme.useless/null/jsonschema/2-01-3",
          "data": null
        }
      """

    SchemaKey.extract(json) must beLeft
  }
}
