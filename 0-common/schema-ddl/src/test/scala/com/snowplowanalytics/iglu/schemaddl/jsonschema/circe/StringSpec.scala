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
package com.snowplowanalytics.iglu.schemaddl.jsonschema.circe

// json4s
import io.circe.literal._

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.StringProperty.{Format, MaxLength, MinLength}

import implicits._

// specs2
import org.specs2.Specification

class StringSpec extends Specification { def is = s2"""
  Check JSON Schema string specification
    parse correct minLength $e1
    parse maxLength with ipv4 format $e2
    parse unknown format $e3
  """

  def e1 = {
    val schema = json"""{"minLength": 32}"""

    Schema.parse(schema) must beSome(Schema(minLength = Some(MinLength(32))))
  }

  def e2 = {
    val schema = json"""{"maxLength": 32, "format": "ipv4"}"""

    Schema.parse(schema) must beSome(Schema(maxLength = Some(MaxLength(32)), format = Some(Format.Ipv4Format)))
  }

  def e3 = {
    val schema = json"""{"maxLength": 32, "format": "unknown"}"""

    Schema.parse(schema) must beSome(Schema(maxLength = Some(MaxLength(32)), format = Some(Format.CustomFormat("unknown"))))
  }
}
