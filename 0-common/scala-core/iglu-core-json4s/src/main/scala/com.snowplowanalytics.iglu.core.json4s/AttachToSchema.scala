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

// json4s
import org.json4s._
import org.json4s.JsonDSL._

// This library
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.Containers.ToSchema

/**
 * [[AttachTo]] type class instance for [[JValue]] JSON Schemas
 *
 * By importing this instance and making it implicit you'll have:
 * + `attachSchemaKey` for all [[JValue]]s
 * + `toSchema` derived from [[ToSchema]] trait
 * + `getSchemaKey` derived from [[ExtractFrom]]
 */
object AttachToSchema extends AttachTo[JValue] with ToSchema[JValue] with ExtractFromSchema {

  /**
   * Attach [[SchemaKey]] to undescribed JSON Schema
   *
   * @param schemaKey Schema info
   * @param schema undescribed JSON Schema
   * @return updated Schema with attached [[SchemaKey]]
   */
  def attachSchemaKey(schemaKey: SchemaKey, schema: JValue): JValue =
    (("self", Extraction.decompose(schemaKey)): JObject).merge(schema)

  /**
   * Get undescribed Schema itself without any Self-describing info
   *
   * @param json self-describing JSON Schema
   * @return underscibed JSON Schema
   */
  def getContent(json: JValue): Option[JValue] =
    removeSelf(json) match {
      case JNothing => None
      case content  => Some(content)
    }

  private[this] def removeSelf(schema: JValue): JValue = schema match {
    case JObject(fields) =>
      fields.filterNot {
        case ("self", JObject(keys)) => intersectsWithSchemakey(keys)
        case _ => false
      }
    case json => json
  }

  private[this] def intersectsWithSchemakey(fields: List[JField]): Boolean =
    fields.map(_._1).toSet.diff(Set("name", "vendor", "format", "version")).isEmpty
}
