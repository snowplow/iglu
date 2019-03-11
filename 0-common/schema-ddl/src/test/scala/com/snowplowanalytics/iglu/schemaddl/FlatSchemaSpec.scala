/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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

import cats.implicits._
import io.circe.literal._

import org.specs2.Specification
import org.specs2.matcher.Matcher

import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Schema, Pointer }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties.{ Type, Description }

import SpecHelpers._

// TODO: oneOf, patternProperties

class FlatSchemaSpec extends Specification { def is = s2"""
    build recognizes a JSON schema without properties $e1
    build recognizes an object property without 'properties' as primitive $e2
    build recognizes an empty self-describing schema as empty FlatSchema $e3
    build recognizes an array as primitive $e4
    build transforms object,string union type into single primitive $e5
    build adds all required properties and skips not-nested required $e6
    nested $e7
  """

  def e1 = {
    val schema = json"""{"type": "object"}""".schema
    // TODO: I expect to see FlatSchema.empty, but root here considered primitive
    val expected = FlatSchema(Set(Pointer.Root -> Schema.empty.copy(`type` = Some(Type.Object)) ), Set.empty)

    FlatSchema.build(schema) must beEqualTo(expected)
  }

  def e2 = {
    val json = json"""
      {
        "type": "object",
        "properties": {
          "nested": {
            "type": "object",
            "properties": {
              "object_without_properties": {
                "type": "object"
              }
            }
          }
        }
      }
    """.schema
    val expected = FlatSchema(Set(
      "/properties/nested/properties/object_without_properties".jsonPointer ->
        json"""{"type": "object"}""".schema),
      Set.empty
    )

    FlatSchema.build(json) must beEqualTo(expected)
  }

  def e3 = {
    val json = json"""
      {
      	"description": "Wildcard schema #1 to match any valid JSON instance",
      	"self": {
      		"vendor": "com.snowplowanalytics.iglu",
      		"name": "anything-a",
      		"format": "jsonschema",
      		"version": "1-0-0"
      	}
      }
    """.schema
    val description = "Wildcard schema #1 to match any valid JSON instance"
    val expected = FlatSchema(Set(
      Pointer.Root -> Schema.empty.copy(description = Some(Description(description))) ), Set.empty)

    FlatSchema.build(json) must beEqualTo(expected)
  }

  def e4 = {
    val schema = json"""
      {
        "type": "object",
        "properties": {
          "foo": {
            "type": "array",
            "items": {
              "type": "string"
            }
          }
        },
        "additionalProperties": false
      }
    """.schema

    val expected = Set(
      "/properties/foo".jsonPointer ->
        json"""{"type": "array", "items": {"type": "string"}}""".schema
    )

    val result = FlatSchema.build(schema)

    result must beEqualTo(FlatSchema(expected, Set.empty))
  }

  def e5 = {
    val schema = json"""
      {
        "type": "object",
        "properties": {
          "foo": {
            "type": ["string", "object"],
            "properties": {
              "one": {
                "type": "string"
              },
              "two": {
                "type": "integer"
              }
            }
          }
        },
        "additionalProperties": false
      }
    """.schema

    val result = FlatSchema.build(schema)

    val expected = FlatSchema(Set(
      "/properties/foo/properties/two".jsonPointer -> json"""{"type": "integer"}""".schema,
      "/properties/foo/properties/one".jsonPointer -> json"""{"type": "string"}""".schema),
      Set.empty)

    result must beEqualTo(expected)
  }

  def e6 = {
    val schema = json"""
      {
        "type": "object",
        "required": ["foo"],
        "properties": {
          "foo": {
            "type": "object",
            "required": ["one"],
            "properties": {
              "one": {
                "type": "string"
              },
              "nonRequiredNested": {
                "type": "object",
                "required": ["nestedRequired"],
                "properties": {
                  "nestedRequired": {"type": "integer"}
                }
              }
            }
          }
        },
        "additionalProperties": false
      }
    """.schema

    val result = FlatSchema.build(schema)

    val expectedRequired = Set("/properties/foo".jsonPointer, "/properties/foo/properties/one".jsonPointer)
    val expectedSubschemas = Set(
      "/properties/foo/properties/nonRequiredNested/properties/nestedRequired".jsonPointer ->
        json"""{"type": "integer"}""".schema,
      "/properties/foo/properties/one".jsonPointer ->
        json"""{"type": "string"}""".schema,
    )

    val required = result.required must bePointers(expectedRequired)
    val subschemas = result.subschemas must beEqualTo(expectedSubschemas)

    required and subschemas
  }


  def e7 = {
    // swap because it is not canonical Schema JSON Pointer
    val accumulator = List("/deeply", "/deeply/nested", "/other/property")
    val result = for {
      current <- Pointer.parseSchemaPointer("/deeply/nested/property").swap
      acc <- accumulator.traverse(p => Pointer.parseSchemaPointer(p).swap)
    } yield FlatSchema.nestedRequired(acc.toSet, current)

    result must beRight(true)
  }

  def bePointers(expected: Set[Pointer.SchemaPointer]): Matcher[Set[Pointer.SchemaPointer]] = { actual: Set[Pointer.SchemaPointer] =>
    val result = s"""|actual: ${actual.toList.map(_.show).sortBy(_.length).mkString(", ")}
                     |expected: ${expected.toList.map(_.show).sortBy(_.length).mkString(", ")}""".stripMargin
    (actual == expected, result)
  }
}
