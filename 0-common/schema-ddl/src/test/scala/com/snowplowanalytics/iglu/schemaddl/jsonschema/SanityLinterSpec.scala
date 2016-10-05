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

import scalaz.{Failure, Success, NonEmptyList}

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

// This libary
import json4s.Json4sToSchema._

class SanityLinterSpec extends Specification { def is = s2"""
  Check SanityLinter specification
    recognize minLength and object type incompatibility $e1
    recognize minimum/maximum incompatibility inside deeply nested Schema (can be unwanted behavior) $e2
    recognize impossibility to fulfill required property $e3
    recognize errors for second severity level $e4
    recognize error in the middle of object $e5
  """

  def e1 = {
    val schema = Schema.parse(parse(
      """
        |{
        |  "type": "object",
        |  "minLength": 3
        |}
      """.stripMargin)).get

    SanityLinter.lint(schema, SanityLinter.FirstLevel) must beEqualTo(Failure(NonEmptyList("Properties [minLength] require string or absent type")))
  }

  def e2 = {
    val schema = Schema.parse(parse(
      """
        |{
        |  "additionalProperties": {
        |    "properties": {
        |      "nestedObject": {
        |        "properties": {
        |          "nestedArray": {
        |            "items": {
        |              "type": "object"
        |            },
        |            "additionalItems": {
        |              "patternProperties": {
        |                "someInvalid": {
        |                  "minimum": 5,
        |                  "maximum": 0
        |                }
        |              }
        |            }
        |          }
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin)).get

    SanityLinter.lint(schema, SanityLinter.FirstLevel) must beEqualTo(Failure(NonEmptyList("minimum property [5] is greater than maximum [0]")))
  }

  def e3 = {
    val schema = Schema.parse(parse(
      """
        |{
        |  "additionalProperties": false,
        |  "properties": {
        |    "oneKey": {}
        |  },
        |  "required": ["oneKey", "twoKey"]
        |}
      """.stripMargin
    )).get

    SanityLinter.lint(schema, SanityLinter.FirstLevel) must beEqualTo(Failure(NonEmptyList("Properties [twoKey] is required, but not listed in properties")))
  }

  def e4 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "object",
        |    "properties": {
        |       "sku": {
        |           "type": "string"
        |       },
        |       "name": {
        |           "type": "string"
        |       },
        |       "category": {
        |           "type": "string"
        |       },
        |       "unitPrice": {
        |           "type": "number"
        |       },
        |       "quantity": {
        |           "type": "number"
        |       },
        |       "currency": {
        |           "type": "string"
        |       }
        |    },
        |    "required": ["sku", "quantity"],
        |    "additionalProperties": false
        |}
      """.stripMargin
    )).get

    SanityLinter.lint(schema, SanityLinter.SecondLevel) must beEqualTo(
      Failure(NonEmptyList(
        "String Schema doesn't contain maxLength nor enum properties nor appropriate format",
        "Numeric Schema doesn't contain minimum and maximum properties",
        "String Schema doesn't contain maxLength nor enum properties nor appropriate format",
        "String Schema doesn't contain maxLength nor enum properties nor appropriate format",
        "String Schema doesn't contain maxLength nor enum properties nor appropriate format",
        "Numeric Schema doesn't contain minimum and maximum properties"
      ))
    )
  }

  def e5 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "object",
        |    "properties": {
        |       "sku": {
        |           "type": "string"
        |       },
        |       "name": {
        |           "type": "string",
        |           "maximum": 0
        |       },
        |       "category": {
        |           "type": "string",
        |           "minimum": 0
        |       },
        |       "unitPrice": {
        |           "type": "number"
        |       },
        |       "quantity": {
        |           "type": "number"
        |       },
        |       "currency": {
        |           "type": "string"
        |       }
        |    },
        |    "required": ["sku", "quantity"],
        |    "additionalProperties": false
        |}
      """.stripMargin
    )).get

    SanityLinter.lint(schema, SanityLinter.FirstLevel) must beEqualTo(
      Failure(NonEmptyList(
        "Properties [maximum] require number, integer or absent type",
        "Properties [minimum] require number, integer or absent type"
      ))
    )

  }
}
