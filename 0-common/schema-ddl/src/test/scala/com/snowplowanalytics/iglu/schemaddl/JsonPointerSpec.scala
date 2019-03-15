/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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

import cats.data.State
import io.circe.literal._

import jsonschema.{Pointer, Schema}
import jsonschema.circe.implicits._

import SpecHelpers._

import org.specs2.Specification

class JsonPointerSpec extends Specification { def is = s2"""
  traverse goes through items property $e1
  traverse goes through additionalItems property $e2
  traverse goes through properties property $e3
  traverse goes through oneOf property $e4
  isParentOf recognizes its parents $e5
  """

  case class SchemaTypes(list: List[(Pointer.SchemaPointer, String)]) {
    def add(pointer: Pointer.SchemaPointer, schemaType: String) = SchemaTypes((pointer, schemaType) :: list)
    def toMap: Map[String, String] = list.toMap.map { case (k ,v) => (k.show, v)}
  }
  val empty = SchemaTypes(Nil)
  def saveType(pointer: Pointer.SchemaPointer, schema: Schema): State[SchemaTypes, Unit] =
    State.modify[SchemaTypes](schemaTypes => schemaTypes.add(pointer, schema.`type`.fold("unknown")(_.toString)))

  def e1 = {
    val json =
      json"""
          {
            "type": "array",
            "items": [
              {"type": "string"},
              {"type": "number"},
              {"maxLength": 32},
              {"items": { "type": "object" }}
            ]
          }
        """

    val schema = json.as[Schema].fold(x => throw x, identity)

    val result = Schema.traverse(schema, saveType).runS(empty).value.toMap
    val expected = Map(
      "/" -> "Array",
      "/items/0" -> "String",
      "/items/1" -> "Number",
      "/items/2" -> "unknown",
      "/items/3" -> "unknown",
      "/items/3/items" -> "Object"
    )

    result must beEqualTo(expected)
  }

  def e2 = {
    val json =
      json"""
          {
            "items": [
              {"type": "string"},
              {"type": "number"},
              {"maxLength": 32},
              {"additionalItems": { "type": "object" }}
            ]
          }
        """

    import cats.implicits._
    val schema = json.as[Schema].fold(x => throw new RuntimeException(x.show), identity)

    val result = Schema.traverse(schema, saveType).runS(empty).value.toMap
    val expected = Map(
      "/" -> "unknown",
      "/items/0" -> "String",
      "/items/1" -> "Number",
      "/items/2" -> "unknown",
      "/items/3" -> "unknown",
      "/items/3/additionalItems" -> "Object"
    )

    result must beEqualTo(expected)
  }

  def e3 = {
    val json =
      json"""
          {
            "type": "object",
            "properties": {
              "someBool": { "type": "boolean" },
              "someArray": {
                "type": "array",
                "items": [{"type": "null"}, {"type": "string"}]
              }
            }
          } """

    val schema = json.as[Schema].fold(x => throw x, identity)

    val result = Schema.traverse(schema, saveType).runS(empty).value.toMap
    val expected = Map(
      "/properties/someBool" -> "Boolean",
      "/properties/someArray" -> "Array",
      "/properties/someArray/items/1" -> "String",
      "/" -> "Object",
      "/properties/someArray/items/0" -> "Null"
    )

    result must beEqualTo(expected)
  }

  def e4 = {
    val json =
      json"""
          {
            "type": "object",
            "properties": {
              "someBool": { "type": "boolean" },
              "withOneOf": {
                "oneOf": [{ "type": "array" },{ "type": "object" }]
              }
            }
          } """

    val schema = json.as[Schema].fold(x => throw x, identity)

    val result = Schema.traverse(schema, saveType).runS(empty).value.toMap
    val expected = Map(
      "/" -> "Object",
      "/properties/withOneOf" -> "unknown",
      "/properties/withOneOf/oneOf/0" -> "Array",
      "/properties/withOneOf/oneOf/1" -> "Object",
      "/properties/someBool" -> "Boolean"
    )

    result must beEqualTo(expected)
  }

  def e5 = {
    "/".jsonPointer.isParentOf("/foo".jsonPointer) and
      "/".jsonPointer.isParentOf("/foo/bar".jsonPointer) and
      (!"/foobar".jsonPointer.isParentOf("/foo/bar".jsonPointer)) and
      "/foo/bar".jsonPointer.isParentOf("/foo/bar/baz".jsonPointer)
  }
}
