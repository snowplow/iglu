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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification


import ArrayProperties._
import json4s.Json4sToSchema._

class ArraySpec extends Specification { def is = s2"""
  Check JSON Schema string specification
    parse correct minItems $e1
    parse incorrect (negative) minItems (DECIDE IF THIS DESIRED) $e2
  """

  def e1 = {
    val schema = parse(
      """
        |{"minItems": 32}
      """.stripMargin)

    Schema.parse(schema) must beSome(Schema(minItems = Some(MinItems(32))))
  }

  def e2 = {
    val schema = parse(
      """
        |{"maxItems": -32}
      """.stripMargin)

    Schema.parse(schema) must beSome(Schema(maxItems = Some(MaxItems(-32))))
  }
}
