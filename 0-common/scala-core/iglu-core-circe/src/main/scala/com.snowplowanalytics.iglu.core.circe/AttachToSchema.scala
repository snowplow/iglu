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
package com.snowplowanalytics.iglu.core.circe

// Circe
import io.circe._
import io.circe.syntax._

// This library
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.Containers.ToSchema

/**
 * [[AttachTo]] instance for [[Json]] Schemas
 *
 * By importing this instance and making it implicit you'll have:
 * + `attachSchemaKey` for all [[Json]]s
 * + `toSchema` derived from [[ToSchema]] trait
 * + `getSchemaKey` derived from [[ExtractFrom]]
 */
object AttachToSchema extends AttachTo[Json] with ToSchema[Json] with ExtractFromSchema {

  implicit val schemaKeyEncoder = CirceIgluCodecs.encodeSchemaKey

  /**
   * Attach [[SchemaKey]] to undescribed JSON Schema
   *
   * @param schemaKey Schema info
   * @param schema undescribed JSON Schema
   * @return updated Schema with attached [[SchemaKey]]
   */
  def attachSchemaKey(schemaKey: SchemaKey, schema: Json): Json =
    Json.obj("self" -> schemaKey.asJson).deepMerge(schema)

  /**
   * Get undescribed Schema itself without any Self-describing info
   *
   * @param json self-describing JSON Schema
   * @return underscibed JSON Schema
   */
  def getContent(json: Json): Option[Json] = for {
    jsonObject <- json.asObject.map(_.toMap)
    schema     <- Some(jsonObject - "self")
  } yield JsonObject.fromMap(schema).asJson
}
