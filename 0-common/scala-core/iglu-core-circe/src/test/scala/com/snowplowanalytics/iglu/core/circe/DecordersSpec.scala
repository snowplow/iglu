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
package com.snowplowanalytics.iglu.core.circe

import cats.implicits._

// circe
import io.circe._
import io.circe.parser.parse

// This library
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.circe.CirceIgluCodecs._

import org.specs2.Specification

class DecordersSpec extends Specification { def is = s2"""
  Circe decoders
    decode SelfDescribingSchema $e1
  """

  def e1 = {
    val result: Json = parse(
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
      """.stripMargin).getOrElse(Json.Null)

    val self = SchemaMap("com.acme", "keyvalue", "jsonschema", SchemaVer.Full(1,1,0))
    val schema = parse(
      """
        |{
        |	"type": "object",
        |	"properties": {
        |		"name": { "type": "string" },
        |		"value": { "type": "string" }
        | }
        |}
      """.stripMargin).getOrElse(Json.Null)

    // With AttachTo[JValue] with ToData[JValue] in scope .toSchema won't be even available
    result.as[SelfDescribingSchema[Json]] must beRight(SelfDescribingSchema(self, schema))
  }
}
