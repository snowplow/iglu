/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
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
package bigquery

class FieldSpec extends org.specs2.Specification { def is = s2"""
  build generates field for object with string and object $e1
  build recognizes numeric properties $e2
  build generates repeated field for array $e3
  build generates repeated nullary field for array $e4
  build uses string-fallback strategy for union types $e5
  build recognized nullable union type $e6
  build generates repeated string for empty schema in items $e7
  build generates repeated record for nullable array $e8
  """

  def e1 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": "object",
        |"properties": {
        |  "stringKey": {
        |    "type": "string",
        |    "maxLength": 500
        |  },
        |  "objectKey": {
        |    "type": "object",
        |    "properties": {
        |      "nestedKey1": { "type": "string" },
        |      "nestedKey2": { "type": ["integer", "null"] },
        |      "nestedKey3": { "type": "boolean" }
        |    },
        |    "required": ["nestedKey3"]
        |  }
        |}
        |}
      """.stripMargin)

    val expected = Field(
      "foo",
      Type.Record(List(
        Field("objectKey",
          Type.Record(List(
            Field("nestedKey3", Type.Boolean, Mode.Required),
            Field("nestedKey1", Type.String, Mode.Nullable),
            Field("nestedKey2", Type.Integer, Mode.Nullable)
          )),
          Mode.Nullable
        ),
        Field("stringKey", Type.String,Mode.Nullable))),
      Mode.Nullable
    )

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e2 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": "object",
        |"properties": {
        |  "numeric1": {"type": "number" },
        |  "numeric2": {"type": "integer" },
        |  "numeric3": {"type": ["number", "null"] },
        |  "numeric4": {"type": ["integer", "null", "number"] }
        |},
        |"required": ["numeric4", "numeric2"]
        |}
      """.stripMargin)

    val expected = Field(
      "foo",
      Type.Record(List(
        Field("numeric2", Type.Integer, Mode.Required),
        Field("numeric1", Type.Float, Mode.Nullable),
        Field("numeric3", Type.Float, Mode.Nullable),
        Field("numeric4", Type.Float, Mode.Nullable)
      )),
      Mode.Nullable
    )

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e3 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": "array",
        |"items": {
        |  "type": "object",
        |  "properties": {
        |    "foo": { "type": "string" },
        |    "bar": { "type": "integer" }
        |  }
        |}
        |}
      """.stripMargin)

    val expected = Field(
      "foo",
      Type.Record(List(
        Field("bar", Type.Integer, Mode.Nullable),
        Field("foo", Type.String, Mode.Nullable)
      )),
      Mode.Repeated
    )

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e4 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": "array",
        |"items": {
        |  "type": ["object", "null"],
        |  "properties": {
        |    "foo": { "type": "string" },
        |    "bar": { "type": "integer" }
        |  }
        |}
        |}
      """.stripMargin)

    val expected = Field(
      "foo",
      Type.Record(List(
        Field("bar", Type.Integer, Mode.Nullable),
        Field("foo", Type.String, Mode.Nullable)
      )),
      Mode.Repeated
    )

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e5 = {
    val input = SpecHelpers.parseSchema(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "union": { "type": ["string", "integer"] }
        |  }
        |}
      """.stripMargin)

    val expected = Field("foo",Type.Record(List(
      Field("union",Type.String,Mode.Nullable)
    )),Mode.Nullable)

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e6 = {
    val input = SpecHelpers.parseSchema(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "union": { "type": ["string", "integer", "null"] }
        |  },
        |  "required": ["union"]
        |}
      """.stripMargin)

    val expected = Field("foo",Type.Record(List(
      Field("union",Type.String,Mode.Nullable)
    )),Mode.Nullable)

    Field.build("foo", input, false) must beEqualTo(expected)
  }

  def e7 = {
    val input = SpecHelpers.parseSchema(
      """
        |  {
        |    "type": "object",
        |    "properties": {
        |      "imp": {
        |        "type": "array",
        |        "items": {}
        |      }
        |    }
        |  }
      """.stripMargin)

    val expected = Field("arrayTest",Type.Record(List(Field("imp",Type.String,Mode.Repeated))),Mode.Required)
    Field.build("arrayTest", input, true) must beEqualTo(expected)
  }

  def e8 = {
    val input = SpecHelpers.parseSchema(
      """
        |  {
        |    "type": "object",
        |    "properties": {
        |      "imp": {
        |        "type": ["array", "null"],
        |        "items": { }
        |      }
        |    }
        |  }
      """.stripMargin)

    val expected = Field("arrayTest",Type.Record(List(Field("imp",Type.String,Mode.Repeated))),Mode.Required)
    Field.build("arrayTest", input, true) must beEqualTo(expected)
  }
}
