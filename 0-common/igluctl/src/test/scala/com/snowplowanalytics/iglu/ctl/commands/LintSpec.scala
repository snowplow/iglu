/*
 * Copyright (c) 2016-2017 Snowplow Analytics Ltd. All rights reserved.
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
package commands

import cats.data.Validated
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SelfSyntaxChecker
import org.json4s.jackson.JsonMethods.parse
import org.specs2.Specification

class LintSpec extends Specification { def is = s2"""
  Linter command (lint) specification
    lint iglu:com.snowplowanalytics.self-desc/instance-iglu-only/jsonschema/1-0-0 $e1
  """

  def e1 = {
    val schema = """
      {
      	"$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
      	"description": "Top-level schema for the validation process (Iglu-only)",
      	"self": {
      		"vendor": "com.snowplowanalytics.self-desc",
      		"name": "instance-iglu-only",
      		"format": "jsonschema",
      		"version": "1-0-0"
      	},

      	"type": "object",

      	"properties": {

      		"schema": {
      			"type": "string",
      			"pattern": "^iglu:[a-zA-Z0-9-_.]+/[a-zA-Z0-9-_]+/[a-zA-Z0-9-_]+/[0-9]+-[0-9]+-[0-9]+$"
      		},

      		"data": {}
      	},

      	"required": ["schema", "data"],
      	"additionalProperties": false
      }"""

    val jsonSchema = parse(schema)

    val result = SelfSyntaxChecker.validateSchema(jsonSchema, false)
    val expected = Validated.Valid(())
    result must beEqualTo(expected)
  }
}
