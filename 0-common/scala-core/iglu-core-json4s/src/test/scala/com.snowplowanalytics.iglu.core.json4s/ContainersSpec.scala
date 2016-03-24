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
package com.snowplowanalytics.iglu.core.json4s

// specs2
import org.specs2.Specification

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// This library
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.Containers._

class ContainersSpec extends Specification { def is = s2"""
  Specification for container types
    extract SelfDescribingData $e1
    extract SelfDescribingSchema $e2
    normalize SelfDescribingData $e3
    normalize SelfDescribingSchema $e4
    stringify SelfDescribingData $e5
    stringify SelfDescribingSchema $e6
  """

  def e1 = {

    implicit val attachSchemaKey = AttachToData

    val result: JValue = parse(
      """
        |{
        | "schema": "iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0",
        | "data": {
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        | }
        |}
      """.stripMargin)

    val key = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0))
    val data = parse(
      """
        |{
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        |}
      """.stripMargin)

    // With AttachTo[JValue] with ToSchema[JValue] in scope .toData won't be even available
    result.toData must beSome(SelfDescribingData(key, data))
  }

  def e2 = {

    implicit val attachSchemaKey = AttachToSchema

    val result: JValue = parse(
      """
        |{
        |	"self": {
        |		"vendor": "com.acme",
        |		"name": "keyvalue",
        |		"format": "jsonschema",
        |		"version": "1-1-0"
        |	},
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        |	}
        |}
      """.stripMargin)

    val self = SchemaKey("com.acme", "keyvalue", "jsonschema", SchemaVer(1,1,0))
    val schema = parse(
      """
        |{
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        | }
        |}
      """.stripMargin)

    // With AttachTo[JValue] with ToData[JValue] in scope .toSchema won't be even available
    result.toSchema must beSome(SelfDescribingSchema(self, schema))
  }

  def e3 = {

    implicit val normalize = NormalizeData

    val schema = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0))
    val data = parse(
      """
        |{
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        |}
      """.stripMargin)

    val expected: JValue = parse(
      """
        |{
        | "schema": "iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0",
        | "data": {
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        | }
        |}
      """.stripMargin
    )

    val result = SelfDescribingData(schema, data).normalize
    result must beEqualTo(expected)
  }

  def e4 = {

    implicit val normalize = NormalizeSchema

    val self = SchemaKey("com.acme", "keyvalue", "jsonschema", SchemaVer(1,1,0))
    val schema = parse(
      """
        |{
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        | }
        |}
      """.stripMargin)

    val expected: JValue = parse(
      """
        {
        	"self": {
        		"vendor": "com.acme",
        		"name": "keyvalue",
        		"format": "jsonschema",
        		"version": "1-1-0"
        	},
        	"type": "object",
        	"properties": {
        		"name": { "type": "string" },
        		"value": { "type": "string" }
        	}
        }
      """)

    val result = SelfDescribingSchema(self, schema)
    result.normalize must beEqualTo(expected)
  }

  def e5 = {

    implicit val stringify: StringifyData[JValue] = StringifyData

    val schema = SchemaKey("com.snowplowanalytics.snowplow", "geolocation_context", "jsonschema", SchemaVer(1,1,0))
    val data: JValue = parse(
      """
        |{
        |  "latitude": 32.2,
        |  "longitude": 53.23,
        |  "speed": 40
        |}
      """.stripMargin)

    val expected: String =
      """{"schema":"iglu:com.snowplowanalytics.snowplow/geolocation_context/jsonschema/1-1-0","data":{"latitude":32.2,"longitude":53.23,"speed":40}}"""

    val result = SelfDescribingData(schema, data).asString
    result must beEqualTo(expected)
  }

  def e6 = {

    implicit val stringify: StringifySchema[JValue] = StringifySchema

    val self = SchemaKey("com.acme", "keyvalue", "jsonschema", SchemaVer(1,1,0))
    val schema: JValue = parse(
      """
        |{
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        | }
        |}
      """.stripMargin)

    val expected: String =
      """{"self":{"vendor":"com.acme","name":"keyvalue","format":"jsonschema","version":"1-1-0"},"type":"object","properties":{"name":{"type":"string"},"value":{"type":"string"}}}"""

    val result = SelfDescribingSchema(self, schema)
    result.asString must beEqualTo(expected)
  }
}
