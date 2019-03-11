/*
 * Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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

import cats.data.NonEmptyList
import org.json4s.jackson.JsonMethods.parse

import org.specs2.Specification

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Pointer.Cursor.{DownField, DownProperty}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Pointer.SchemaProperty.Properties
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.Level.Warning
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.Message

class SelfSyntaxCheckerSpec extends Specification { def is = s2"""
  Recognize invalid property $e1
  """

  def e1 = {
    val jsonSchema = parse(
      """{
        |    "$schema" : "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
        |    "description": "Schema for an example event",
        |    "self": {
        |        "vendor": "com.snowplowanalytics",
        |        "name": "example_event",
        |        "format": "jsonschema",
        |        "version": "1-0-0"
        |    },
        |    "type": "object",
        |    "properties": {
        |        "example_field_1": {
        |            "type": "string",
        |            "description": "the example_field_1 means x",
        |            "maxLength": 128
        |        },
        |        "example_field_2": {
        |            "type": ["string", "null"],
        |            "description": "the example_field_2 means y",
        |            "maxLength": 128
        |        },
        |        "example_field_3": {
        |            "type": "array",
        |            "description": "the example_field_3 is a collection of user names",
        |            "users": {
        |                "type": "object",
        |                "properties": {
        |                    "name": {
        |                        "type": "string",
        |                        "maxLength": 128
        |                    }
        |                },
        |                "required": [
        |                    "id"
        |                ],
        |                "additionalProperties": false
        |            }
        |        }
        |    },
        |    "additionalProperties": false,
        |    "required": [
        |        "example_field_1",
        |        "example_field_3"
        |    ]
        |}""".stripMargin)

    val expected = NonEmptyList.of(Message(
      Pointer.SchemaPointer(List(DownField("example_field_3"), DownProperty(Properties))),
      "The following keywords are unknown and will be ignored: [users]",
      Warning))

    SelfSyntaxChecker.validateSchema(jsonSchema, false).toEither must beLeft(expected)
  }
}
