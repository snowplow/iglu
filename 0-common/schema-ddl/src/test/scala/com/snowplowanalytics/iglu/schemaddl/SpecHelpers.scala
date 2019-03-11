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

import io.circe.Json

import org.json4s.jackson.parseJson

import jsonschema.{ Schema, Pointer }
import jsonschema.json4s.implicits._
import jsonschema.circe.implicits._

object SpecHelpers {
  def parseSchema(string: String): Schema = {
    Schema
      .parse(parseJson(string))
      .getOrElse(throw new RuntimeException("SpecHelpers.parseSchema received invalid JSON Schema"))
  }

  implicit class JsonOps(json: Json) {
    def schema: Schema =
      Schema.parse(json).getOrElse(throw new RuntimeException("SpecHelpers.parseSchema received invalid JSON Schema"))
  }

  implicit class StringOps(str: String) {
    def jsonPointer: Pointer.SchemaPointer =
      Pointer.parseSchemaPointer(str).fold(x => x, x => x)
  }
}
