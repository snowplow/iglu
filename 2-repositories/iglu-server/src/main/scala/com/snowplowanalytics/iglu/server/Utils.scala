/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.iglu.server

import org.json4s.JValue
import org.json4s.jackson.JsonMethods.{ compact, parse => parseJson4s }

import io.circe.{Encoder, Json}
import io.circe.syntax._
import io.circe.jawn.parse

import fs2.{ Stream, text }

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaMap, SchemaVer}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.implicits._


object Utils {


  // TODO: factor out json4s from Iglu entirely
  implicit class DowngradeJson(json: Json) {
    def fromCirce: JValue = parseJson4s(json.noSpaces)
  }

  implicit class UpgradeSchema(schema: Schema) {
    def toCirce: Json =
      parse(compact(Schema.normalize(schema)))
        .getOrElse(throw new RuntimeException("Unexpected JSON conversion exception"))
  }

  def toSchemaMap(schemaKey: SchemaKey): SchemaMap =
    SchemaMap(schemaKey.vendor, schemaKey.name, schemaKey.format, schemaKey.version.asInstanceOf[SchemaVer.Full])

  def toBytes[F[_], A: Encoder](a: A): Stream[F, Byte] =
    Stream.emit(a.asJson.noSpaces).through(text.utf8Encode)

}
