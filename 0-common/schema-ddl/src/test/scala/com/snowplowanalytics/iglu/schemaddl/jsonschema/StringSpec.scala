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


import StringProperties._
import json4s.Json4sToSchema._

class StringSpec extends Specification { def is = s2"""
  Check JSON Schema string specification
    parse correct minLength $e1
    parse maxLength with ipv4 format $e2
    parse unknown format $e3
  """

  def e1 = {
    val schema = parse(
      """
        |{"minLength": 32}
      """.stripMargin)

    Schema.parse(schema) must beSome(Schema(minLength = Some(MinLength(32))))
  }

  def e2 = {
    val schema = parse(
      """
        |{"maxLength": 32, "format": "ipv4"}
      """.stripMargin)

    Schema.parse(schema) must beSome(Schema(maxLength = Some(MaxLength(32)), format = Some(Ipv4Format)))
  }

  def e3 = {
    val schema = parse(
      """
        |{"maxLength": 32, "format": "unknown"}
      """.stripMargin)

    Schema.parse(schema) must beSome(Schema(maxLength = Some(MaxLength(32)), format = Some(CustomFormat("unknown"))))
  }
}
