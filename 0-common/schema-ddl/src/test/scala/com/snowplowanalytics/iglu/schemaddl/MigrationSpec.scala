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

// Scala
import scala.collection.immutable.ListMap

// scalaz
import scalaz._

// Iglu Core
import com.snowplowanalytics.iglu.core.{ SchemaMap, SchemaVer }
import com.snowplowanalytics.iglu.core.SelfDescribingSchema

// specs2
import org.specs2.Specification

// json4s
import org.json4s.jackson.JsonMethods.parse

// This library
import Migration._

class MigrationSpec extends Specification { def is = s2"""
  Check common Schema migrations
    create correct addition migration from 1-0-0 to 1-0-1 $e1
    create correct addition migrations from 1-0-0 to 1-0-2 $e2
  """

  def e1 = {
    val initial = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)
    val initialSchema = SelfDescribingSchema(SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,0)), initial)

    val second = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    },
        |    "bar": {
        |      "type": "integer",
        |      "maximum": 4000
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)
    val secondSchema = SelfDescribingSchema(SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,1)), second)

    val migrations = Success(List(
      Migration(
        "com.acme",
        "example",
        SchemaVer.Full(1,0,0),
        SchemaVer.Full(1,0,1),
        SchemaDiff(
          ListMap("bar" -> Map("type" -> "integer", "maximum" -> "4000")),
          ListMap.empty[String, Map[String, String]],
          Set.empty))))

    val migrationMap = Map(
      SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,0)) -> migrations
    )

    Migration.buildMigrationMap(List(secondSchema, initialSchema)) must beEqualTo(migrationMap)
  }

  def e2 = {
    val initial = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)
    val initialSchema = SelfDescribingSchema(SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,0)), initial)

    val second = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    },
        |    "bar": {
        |      "type": "integer",
        |      "maximum": 4000
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)
    val secondSchema = SelfDescribingSchema(SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,1)), second)

    val third = parse(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    },
        |    "bar": {
        |      "type": "integer",
        |      "maximum": 4000
        |    },
        |    "baz": {
        |      "type": "array"
        |    }
        |  },
        |  "additionalProperties": false
        |}
      """.stripMargin)
    val thirdSchema = SelfDescribingSchema(SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,2)), third)

    val migrations1 = Success(List(
      Migration(
        "com.acme",
        "example",
        SchemaVer.Full(1,0,0),
        SchemaVer.Full(1,0,2),
        SchemaDiff(
          ListMap("bar" -> Map("type" -> "integer", "maximum" -> "4000"), "baz" -> Map("type" -> "array")),
          ListMap.empty[String, Map[String, String]],
          Set.empty)),
      Migration(
        "com.acme",
        "example",
        SchemaVer.Full(1,0,0),
        SchemaVer.Full(1,0,1),
        SchemaDiff(
          ListMap("bar" -> Map("type" -> "integer", "maximum" -> "4000")),
          ListMap.empty[String, Map[String, String]],
          Set.empty))))

    val migrations2 = Success(List(
      Migration(
        "com.acme",
        "example",
        SchemaVer.Full(1,0,1),
        SchemaVer.Full(1,0,2),
        SchemaDiff(
          ListMap("baz" -> Map("type" -> "array")),
          ListMap.empty[String, Map[String, String]],
          Set.empty))
    ))

    val migrationMap = Map(
      SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,1)) -> migrations2,
      SchemaMap("com.acme", "example", "jsonschema", SchemaVer.Full(1,0,0)) -> migrations1
    )

    Migration.buildMigrationMap(List(secondSchema, thirdSchema, initialSchema)) must beEqualTo(migrationMap)
  }
}
