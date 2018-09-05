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
package com.snowplowanalytics.iglu.schemaddl.jsonschema.circe

import io.circe.literal._

import com.snowplowanalytics.iglu.schemaddl.jsonschema.ObjectProperty.{Properties, Required}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{Schema, StringProperty}

import implicits._

// specs2
import org.specs2.Specification

class ObjectSpec extends Specification { def is = s2"""
  Check JSON Schema object specification
    parse object with empty properties $e1
    parse object with one property $e2
    parse object with several subschemas $e3
    parse object with required property $e4
  """

  def e1 = {

    val schema = json"""{ "properties": {}}"""

    Schema.parse(schema) must beSome(Schema(properties = Some(Properties(Map.empty[String, Schema]))))
  }

  def e2 = {
    val schema = json"""
        {
          "properties": {
            "key": {}
          }
        }
      """

    Schema.parse(schema) must beSome(Schema(properties = Some(Properties(Map("key" -> Schema())))))
  }


  def e3 = {

    val schema = json"""
        {
          "properties": {
            "innerKey": {
              "minLength": 32
            }
          }
        }
      """

    Schema.parse(schema) must beSome(Schema(properties = Some(Properties(Map("innerKey" -> Schema(minLength = Some(StringProperty.MinLength(32))))))))
  }

  def e4 = {
    val schema = json""" { "required": ["one", "key", "23"] } """

    Schema.parse(schema) must beSome(Schema(required = Some(Required(List("one", "key", "23")))))
  }
}
