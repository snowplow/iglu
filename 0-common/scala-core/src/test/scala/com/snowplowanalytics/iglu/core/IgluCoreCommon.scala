/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.core

// json4s
import org.json4s._
import org.json4s.JsonDSL._

object IgluCoreCommon {
  /**
   * Example of [[SelfDescribed]] instance for json4s
   */
  implicit object Json4sInstance extends SelfDescribed[JValue] {
    def getSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "schema") match {
        case JString(schema) => SchemaKey.fromUri(schema)
        case _               => None
      }
  }

  /**
   * Helper class bearing its Schema
   */
  case class DescribedString(key: SchemaKey, data: String)

  /**
   * Example of [[SelfDescribed]] instance for usual case class
   */
  implicit object DescribedStringInstance extends SelfDescribed[DescribedString] {
    def getSchemaKey(entity: DescribedString): Option[SchemaKey] =
      Some(entity.key)
  }

}
