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
import com.snowplowanalytics.iglu.core.Containers.ToData

/**
 * [[AttachTo]] instance for [[JValue]] data instance
 *
 * By importing this instance and making it implicit you'll have:
 * + `attachSchemaKey` for all [[JValue]] objects
 * + `toData` derived from [[ToData]] trait
 * + `getSchemaKey` derived from [[ExtractFrom]]
 */
object AttachToData extends AttachTo[JValue] with ToData[JValue] with ExtractFromData {

  /**
   * Attach [[SchemaKey]] to undescribed JSON instance
   *
   * @param schemaKey Schema description or reference to it
   * @param instance undescribed JSON
   * @return updated entity with attached [[SchemaKey]]
   */
  def attachSchemaKey(schemaKey: SchemaKey, instance: JValue): JValue =
    ("schema" -> schemaKey.toSchemaUri) ~ ("data" -> instance)

  /**
   * Get data itself from self-describing JSON data instance
   *
   * @param json self-describing JSON
   * @return entity without [[SchemaKey]]
   */
  def getContent(json: JValue): Option[JValue] =
    json \ "data" match {
      case JNothing     => None
      case data: JValue => Some(data)
    }
}
