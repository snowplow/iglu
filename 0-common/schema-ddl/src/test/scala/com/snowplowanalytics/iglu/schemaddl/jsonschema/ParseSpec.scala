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

// This library
import properties._
import ObjectProperty._
import NumberProperty._
import ArrayProperty._
import StringProperty._
import CommonProperties._

import json4s.implicits._

class ParseSpec extends Specification { def is = s2"""
  Check JSON Schema string specification
    parse big nested Schema AST e1
    normalize big nested Schema AST e2
    parse fails to parse non-Object e3
    parse single-level Schema AST $e4
  """

  def e1 = {

    val schema = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "someString": {
        |      "type": "string"
        |    },
        |    "netstedObject": {
        |      "type": "object",
        |      "properties": {
        |        "nestedKey": {
        |          "type": "null"
        |        },
        |        "deeply": {
        |          "type": "object",
        |          "properties": {
        |            "blueDeep": {
        |              "type": "integer",
        |              "minimum": 0,
        |              "maximum": 32767
        |            },
        |            "deepUnionTypeArray": {
        |              "type": "array",
        |              "items": {
        |                "type": [
        |                  "object",
        |                  "string",
        |                  "null",
        |                  "integer"
        |                ],
        |                "properties": {
        |                  "foo": {
        |                    "type": "string"
        |                  }
        |                },
        |                "additionalProperties": false,
        |                "minimum": 0,
        |                "maximum": 32767
        |              }
        |            }
        |          },
        |          "additionalProperties": false
        |        }
        |      },
        |      "additionalProperties": false
        |    },
        |    "emptyObject": {
        |      "type": "object",
        |      "properties": {},
        |      "additionalProperties": false
        |    },
        |    "simpleArray": {
        |      "type": "array",
        |      "items": {
        |        "type": "integer",
        |        "minimum": 0,
        |        "maximum": 32767
        |      }
        |    },
        |    "ipAddress": {
        |      "type": "string",
        |      "format": "ipv4"
        |    },
        |    "email": {
        |      "type": "string",
        |      "format": "email"
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)

    implicit def optconv[A](a: A): Option[A] = Some(a)

    val resultSchema = Schema(
      `type` = Type.Object,
      properties = Properties(Map(
        "ipAddress" -> Schema(
          `type` = Type.String,
          format = Format.Ipv4Format
        ),
        "email" -> Schema(
          `type` = Type.String,
          format = Format.EmailFormat
        ),
        "someString" -> Schema(
          `type` = Type.String
        ),
        "netstedObject" -> Schema(
          `type` = Type.Object,
          properties = Properties(Map(
            "nestedKey" -> Schema(
              `type` = Type.Null
            ),
            "deeply" -> Schema(
              `type` = Type.Object,
              properties = Properties(Map(
                "blueDeep" -> Schema(
                  `type` = Type.Integer,
                  minimum =  Minimum.IntegerMinimum(0),
                  maximum = Maximum.IntegerMaximum(32767)
                ),
                "deepUnionTypeArray" -> Schema(
                  `type` = Type.Array,
                  items = Items.ListItems(Schema(
                    `type` = Type.Product(List(Type.Object, Type.String, Type.Null, Type.Integer)),
                    properties = Properties(Map(
                      "foo" -> Schema(
                        `type` = Type.String
                      )
                    )),
                    additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false),
                    minimum = Minimum.IntegerMinimum(0),
                    maximum = Maximum.IntegerMaximum(32767)
                  ))
                )
              )),
              additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
            )
          )),
          additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
        ),
        "emptyObject" -> Schema(
          `type` = Type.Object,
          properties = Properties(Map()),
          additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
        ),
        "simpleArray" -> Schema(
          `type` = Type.Array,
          items = Items.ListItems(Schema(
            `type` = Type.Integer,
            minimum = Minimum.IntegerMinimum(0),
            maximum = Maximum.IntegerMaximum(32767)
          ))
        )
      )),
      additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
    )

    Schema.parse(schema) must beSome(resultSchema)
  }

  def e2 = {

    import ObjectProperty._
    import NumberProperty._
    import ArrayProperty._
    import StringProperty._
    import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._
    import json4s.Formats._

    implicit def optconv[A](a: A): Option[A] = Some(a)

    val sourceSchema = Schema(
      `type` = Type.Object,
      properties = Properties(Map(
        "ipAddress" -> Schema(
          `type` = Type.String,
          format = Format.Ipv4Format
        ),
        "email" -> Schema(
          `type` = Type.String,
          format = Format.EmailFormat
        ),
        "someString" -> Schema(
          `type` = Type.String
        ),
        "netstedObject" -> Schema(
          `type` = Type.Object,
          properties = Properties(Map(
            "nestedKey" -> Schema(
              `type` = Type.Null
            ),
            "deeply" -> Schema(
              `type` = Type.Object,
              properties = Properties(Map(
                "blueDeep" -> Schema(
                  `type` = Type.Integer,
                  minimum = Minimum.IntegerMinimum(0),
                  maximum = Maximum.IntegerMaximum(32767)
                ),
                "deepUnionTypeArray" -> Schema(
                  `type` = Type.Array,
                  items = Items.ListItems(Schema(
                    `type` = Type.Product(List(Type.Object, Type.String, Type.Null, Type.Integer)),
                    properties = Properties(Map(
                      "foo" -> Schema(
                        `type` = Type.String
                      )
                    )),
                    additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false),
                    minimum = Minimum.IntegerMinimum(0),
                    maximum = Maximum.IntegerMaximum(32767)
                  ))
                )
              )),
              additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
            )
          )),
          additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
        ),
        "emptyObject" -> Schema(
          `type` = Type.Object,
          properties = Properties(Map()),
          additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
        ),
        "simpleArray" -> Schema(
          `type` = Type.Array,
          items = Items.ListItems(Schema(
            `type` = Type.Integer,
            minimum = Minimum.IntegerMinimum(0),
            maximum = Maximum.IntegerMaximum(32767)
          ))
        )
      )),
      additionalProperties = AdditionalProperties.AdditionalPropertiesAllowed(false)
    )

    val resultSchema = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "someString": {
        |      "type": "string"
        |    },
        |    "netstedObject": {
        |      "type": "object",
        |      "properties": {
        |        "nestedKey": {
        |          "type": "null"
        |        },
        |        "deeply": {
        |          "type": "object",
        |          "properties": {
        |            "blueDeep": {
        |              "type": "integer",
        |              "minimum": 0,
        |              "maximum": 32767
        |            },
        |            "deepUnionTypeArray": {
        |              "type": "array",
        |              "items": {
        |                "type": [
        |                  "object",
        |                  "string",
        |                  "null",
        |                  "integer"
        |                ],
        |                "properties": {
        |                  "foo": {
        |                    "type": "string"
        |                  }
        |                },
        |                "additionalProperties": false,
        |                "minimum": 0,
        |                "maximum": 32767
        |              }
        |            }
        |          },
        |          "additionalProperties": false
        |        }
        |      },
        |      "additionalProperties": false
        |    },
        |    "emptyObject": {
        |      "type": "object",
        |      "properties": {},
        |      "additionalProperties": false
        |    },
        |    "simpleArray": {
        |      "type": "array",
        |      "items": {
        |        "type": "integer",
        |        "minimum": 0,
        |        "maximum": 32767
        |      }
        |    },
        |    "ipAddress": {
        |      "type": "string",
        |      "format": "ipv4"
        |    },
        |    "email": {
        |      "type": "string",
        |      "format": "email"
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)

    Extraction.decompose(sourceSchema) must beEqualTo(resultSchema)
  }

  def e3 = {
    val schema = parse(
      """
        |[]
      """.stripMargin)

    Schema.parse(schema) must beNone
  }

  def e4 = {
    val schema = parse(
      """
        |{}
      """.stripMargin)

    Schema.parse(schema) must beSome
  }
}
