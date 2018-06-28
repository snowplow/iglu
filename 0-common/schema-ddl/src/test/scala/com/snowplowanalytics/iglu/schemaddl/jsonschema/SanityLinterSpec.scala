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

import scalaz.{Failure, NonEmptyList}

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

// This libary
import json4s.Json4sToSchema._
import SanityLinter._

class SanityLinterSpec extends Specification { def is = s2"""
  Check SanityLinter specification
    recognize minLength and object type incompatibility $e1
    recognize minimum/maximum incompatibility inside deeply nested Schema (can be unwanted behavior) $e2
    recognize impossibility to fulfill required property $e3
    recognize schema doesn't contain description property $e4
    recognize error in the middle of object $e5
    recognize root of schema has type non-object $e6
    recognize non-required properties don't have type null $e7
    recognize unknown formats $e8
    recognize maxLength is greater than Redshift VARCHAR(max) $e9
    recognize skipped checks (description) $e10
  """

  def e1 = {
    val schema = Schema.parse(parse(
      """
        |{
        |  "type": "object",
        |  "minLength": 3
        |}
      """.stripMargin)).get

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "String properties [minLength] require string or absent type",
        "Root of schema should have type object and contain properties"
      ))
    )
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

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Root of schema should have type object and contain properties",
        "Schema doesn't contain description property",
        "Schema doesn't contain description property",
        "Schema doesn't contain description property",
        "Schema doesn't contain description property",
        "Schema doesn't contain description property",
        "Schema with numeric type has minimum property [5] greater than maximum [0]",
        "Schema doesn't contain description property"
      ))
    )
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

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Required properties [twoKey] doesn't exist in properties",
        "Root of schema should have type object and contain properties",
        "Schema doesn't contain description property"
      ))
    )
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

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Optional field doesn't allow null type",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties"
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

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Optional field doesn't allow null type",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Numeric properties [maximum] require number, integer or absent type",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Numeric properties [minimum] require number, integer or absent type",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties"
      ))
    )
  }

  def e6 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "array",
        |    "items": {
        |        "type": "object",
        |        "properties": {
        |            "schema": {
        |                "type": "string",
        |                "pattern": "^iglu:[a-zA-Z0-9-_.]+/[a-zA-Z0-9-_]+/[a-zA-Z0-9-_]+/[0-9]+-[0-9]+-[0-9]+$"
        |            },
        |            "data": {}
        |        },
        |        "required": ["schema", "data"],
        |        "additionalProperties": false }
        |    }
        |}
      """.stripMargin
    )).get

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Root of schema should have type object and contain properties",
        "Schema doesn't contain description property",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property"
      ))
    )
  }

  def e7 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "object",
        |    "properties": {
        |      "name": {
        |        "type": "string"
        |      },
        |      "age": {
        |        "type": "number"
        |      }
        |    },
        |    "required":["name"]
        |}
      """.stripMargin
    )).get

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Optional field doesn't allow null type",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties"
      ))
    )
  }

  def e8 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "object",
        |    "properties": {
        |      "name": {
        |        "type": "string",
        |        "format": "camelCase"
        |      },
        |      "age": {
        |        "type": "number"
        |      }
        |    },
        |    "required":["name"]
        |}
      """.stripMargin
    )).get


    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Optional field doesn't allow null type",
        "Schema doesn't contain description property",
        "Schema with string type doesn't contain maxLength property or other ways to extract max length",
        "Unknown format [camelCase] detected. Known formats are: date-time, date, email, hostname, ipv4, ipv6, uri",
        "Schema doesn't contain description property",
        "Schema with numeric type doesn't contain minimum and maximum properties"
      ))
    )
  }

  def e9 = {
    val schema = Schema.parse(parse(
      """
        |{
        |  "type": "string",
        |  "minLength": 3,
        |  "maxLength": 65536
        |}
      """.stripMargin)).get

    lint(schema, 0, allLinters.values.toList) must beEqualTo(
      Failure(NonEmptyList(
        "Schema doesn't contain description property",
        "Schema with string type has maxLength property [65536] greater than Redshift VARCHAR(max) 65535",
        "Root of schema should have type object and contain properties"
      ))
    )
  }

  def e10 = {
    val schema = Schema.parse(parse(
      """
        |{
        |    "type": "object",
        |    "description": "Placeholder object",
        |    "properties": {
        |       "sku": {
        |           "type": "string",
        |           "maxLength": 10
        |       },
        |       "name": {
        |           "type": "string",
        |           "maxLength": 10
        |       },
        |       "category": {
        |           "type": "string",
        |           "maxLength": 10
        |       },
        |       "unitPrice": {
        |           "type": "number",
        |           "minimum": 0,
        |           "maximum": 1
        |       },
        |       "quantity": {
        |           "type": "number",
        |           "minimum": 0,
        |           "maximum": 1,
        |           "description": "Quantity (whole number)"
        |       },
        |       "currency": {
        |           "type": "string",
        |           "maxLength": 10,
        |           "description": "Store currency code"
        |       }
        |    },
        |    "required": ["sku", "quantity"],
        |    "additionalProperties": false
        |}
      """.stripMargin
    )).get

    val skippedLinters = List(lintDescription)

    lint(schema, 0, allLinters.values.toList.diff(skippedLinters)) must beEqualTo(
      Failure(NonEmptyList(
        "Optional field doesn't allow null type"
      ))
    )
  }
}
