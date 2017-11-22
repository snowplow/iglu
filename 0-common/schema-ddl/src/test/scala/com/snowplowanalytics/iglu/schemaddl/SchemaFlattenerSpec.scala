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

// scalaz
import scalaz.Success

// Scala
import scala.collection.immutable.ListMap
import scala.io.Source

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

class SchemaFlattenerSpec extends Specification { def is = s2"""
  Check FlatSchema
    split product types $e1
    stringify JSON array $e2
    fail to stringify object in JSON array $e3
    get required properties $e4
    process properties $e5
    process properties with nested nullable $e6
    process Schema with no properties or patternProperties specified $e7
    process key with no properties or patternProperties specified as string $e8
    handle anything schema $e9
    handle nested properties $e10
  """

  def e1 = {
    val property = Map("test_key" -> Map("type" -> "string,null,integer", "maxLength" -> "32", "maximum" -> "1024"))
    val result = Map(
      "test_key_string" -> Map("type" -> "string,null",  "maxLength" -> "32", "maximum" -> "1024"),
      "test_key_integer" -> Map("type" -> "integer,null",  "maxLength" -> "32", "maximum" -> "1024")
    )

    FlatSchema.splitProductTypes(property) must beEqualTo(result)
  }

  def e2 = {
    val jValues = List(JInt(3), JNull, JString("str"), JDecimal(3.3), JDouble(3.13), JString("another_str"))
    FlatSchema.stringifyArray(jValues).toEither must beRight("3,null,str,3.3,3.13,another_str")
  }

  def e3 = {
    val jValues = List(JInt(3), JNull, JString("str"), JDecimal(3.3), JDouble(3.13), JString("another_str"), JObject(List(("keyOfFatum", JInt(42)))))
    FlatSchema.stringifyArray(jValues).toEither must beLeft
  }

  def e4 = {
    implicit val formats = DefaultFormats
    val json: JObject = parse(Source.fromURL(getClass.getResource("/schema_with_required_properties.json")).mkString).asInstanceOf[JObject]
    val map = json.extract[Map[String, JValue]]

    FlatSchema.getRequiredProperties(map).toEither must beRight(List("anotherRequired", "requiredKey"))
                                                         // getRequiredProperties reverses values ^^^
  }

  def e5 = {
    val json: JObject = parse(Source.fromURL(getClass.getResource("/schema_with_required_properties.json")).mkString).asInstanceOf[JObject]
    val root = List(("root", json))
    val resultMap = Map(
      "root.oneKey" -> Map("type" -> "string"),
      "root.requiredKey" -> Map("type" -> "integer"),
      "root.objectKey.skippedRequired" -> Map("type" -> "string"),
      "root.anotherRequired" -> Map("type" -> "string")
    )

    FlatSchema.processProperties(root).toEither must beRight(FlatSchema.SubSchema(resultMap, Set()))
  }

  def e6 = {
    val json: JObject = parse(Source.fromURL(getClass.getResource("/schema_with_required_properties.json")).mkString).asInstanceOf[JObject]
    val root = List(("root", json))
    val resultMap = Map(
      "root.oneKey" -> Map("type" -> "string"),
      "root.requiredKey" -> Map("type" -> "integer"),
      "root.objectKey.skippedRequired" -> Map("type" -> "string"),
      "root.anotherRequired" -> Map("type" -> "string")
    )

    FlatSchema.processProperties(root, requiredAccum = Set("root")).toEither must beRight(FlatSchema.SubSchema(resultMap, Set("root.requiredKey", "root.anotherRequired")))
  }

  def e7 = {
    val schema = parse("""{"type": "object"}""")

    FlatSchema.flattenJsonSchema(schema, splitProduct = true).toEither must beRight.like {
      case flatSchema => flatSchema must beEqualTo(FlatSchema(ListMap.empty[String, Map[String, String]]))
    }
  }

  def e8 = {
    val json = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "nested": {
        |      "type": "object",
        |      "properties": {
        |        "object_without_properties": {
        |          "type": "object"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin)

    FlatSchema.flattenJsonSchema(json, splitProduct = true).toEither must beRight.like {
      case flatSchema => flatSchema must beEqualTo(FlatSchema(ListMap("nested.object_without_properties" -> Map("type" -> "string"))))
    }
  }

  def e9 = {
    val json = parse(
      """
        |{
        |	"description": "Wildcard schema #1 to match any valid JSON instance",
        |	"self": {
        |		"vendor": "com.snowplowanalytics.iglu",
        |		"name": "anything-a",
        |		"format": "jsonschema",
        |		"version": "1-0-0"
        |	}
        |}
      """.stripMargin)

    FlatSchema.flattenJsonSchema(json, splitProduct = true).toEither must beRight.like {
      case flatSchema => flatSchema must beEqualTo(FlatSchema(ListMap.empty[String, Map[String, String]]))
    }
  }

  def e10 = {
    val schema = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "array",
        |      "items": {
        |        "type": "string"
        |      }
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)

    // TODO: it should include information about `items`
    val result = ListMap("foo" -> Map("type" -> "array"))

    FlatSchema.flattenJsonSchema(schema, splitProduct = false) mustNotEqual Success(FlatSchema(result, Set.empty))
  }.orSkip
}
