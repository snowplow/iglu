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
package com.snowplowanalytics.iglu.schemaddl
package jsonschema

import cats.data.NonEmptyList

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

import io.circe.literal._

// specs2
import org.specs2.Specification

// This libary
import json4s.implicits._
import SanityLinter._
import SpecHelpers._

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
    selected formats will not fail with warning for 'no maxLength' $e11
  """

  def showReport(kv: (Pointer.SchemaPointer, NonEmptyList[Linter.Issue])): (String, NonEmptyList[String]) =
    kv match { case (k, v) => (k.show, v.map(_.show)) }

  def e1 = {
    val schema = json"""{ "type": "object", "minLength": 3 }""".schema

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(
      Map(
        "/" -> NonEmptyList.of("The schema is missing the \"description\" property",
          "String properties [minLength] require either string or absent values",
          "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property")
      )
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

    val expected = Map(
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property"),
      "/additionalProperties" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/additionalProperties/properties/nestedObject" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/additionalProperties/properties/nestedObject/properties/nestedArray/items" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/additionalProperties/properties/nestedObject/properties/nestedArray/additionalItems" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/additionalProperties/properties/nestedObject/properties/nestedArray" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/additionalProperties/properties/nestedObject/properties/nestedArray/additionalItems/patternProperties/someInvalid" ->
        NonEmptyList.of("A field with numeric type has a minimum value [5] greater than the maximum value [0]",
          "The schema is missing the \"description\" property")
    )

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/" -> NonEmptyList.of(
        "The schema is missing the \"description\" property",
        "Elements specified as required [twoKey] don't exist in schema properties",
        "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property"),
      "/properties/oneKey" ->
        NonEmptyList.of("The schema is missing the \"description\" property")
    )

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/properties/unitPrice" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/currency" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/properties/quantity" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/category" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/properties/name" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/properties/sku" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property",
          "Use \"type: null\" to indicate a field as optional for properties name,currency,category,unitPrice")
    )

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/properties/unitPrice" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/currency" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/properties/quantity" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/category" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required", "Numeric properties [minimum] require either a number, integer or absent values"),
      "/properties/name" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required", "Numeric properties [maximum] require either a number, integer or absent values"),
      "/properties/sku" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "Use \"type: null\" to indicate a field as optional for properties name,currency,category,unitPrice"))

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/items/properties/data" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/items/properties/schema" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/items" ->
        NonEmptyList.of("The schema is missing the \"description\" property"),
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property"))

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/properties/age" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/name" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required"),
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "Use \"type: null\" to indicate a field as optional for properties age"))

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/properties/age" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A numeric property should have \"minimum\" and \"maximum\" properties"),
      "/properties/name" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "A string type in the schema doesn't contain \"maxLength\" or format which is required",
          "Unknown format [camelCase] detected. Known formats are: date-time, date, email, hostname, ipv4, ipv6 or uri"),
      "/" ->
        NonEmptyList.of("The schema is missing the \"description\" property", "Use \"type: null\" to indicate a field as optional for properties age"))

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val expected = Map(
      "/" -> NonEmptyList.of(
        "The schema is missing the \"description\" property",
        "A string property has a \"maxLength\" [65536] greater than the Redshift VARCHAR maximum of 65535",
        "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property"))

    lint(schema, Linter.allLintersMap.values.toList).map(showReport) must beEqualTo(expected)
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

    val skippedLinters = List(Linter.description)

    val expected = Map("/" ->
      NonEmptyList.of("Use \"type: null\" to indicate a field as optional for properties name,currency,category,unitPrice"))

    lint(schema, Linter.allLintersMap.values.toList.diff(skippedLinters)).map { case (k, v) => (k.show, v.map(_.show)) } must beEqualTo(expected)
  }

  def e11 = {
    val schema = Schema.parse(parse(
      """
        |{
        |   "type": "object",
        |   "description": "desc text",
        |   "properties": {
        |       "emailField": {
        |           "type": "string",
        |           "format": "email"
        |       },
        |       "dateField": {
        |           "type": "string",
        |           "format": "date"
        |       },
        |       "uriField": {
        |           "type": "string",
        |           "format": "uri"
        |       },
        |       "hostnameField": {
        |           "type": "string",
        |           "format": "hostname"
        |       },
        |       "uuidField": {
        |           "type": "string",
        |           "format": "uuid"
        |       }
        |   },
        |   "additionalProperties": false    
        |}
      """.stripMargin
    )).get
 
    val skippedLinters = List(Linter.description)
 
    lint(schema, Linter.allLintersMap.values.toList.diff(skippedLinters)) must beEqualTo(Map())
  }
}
