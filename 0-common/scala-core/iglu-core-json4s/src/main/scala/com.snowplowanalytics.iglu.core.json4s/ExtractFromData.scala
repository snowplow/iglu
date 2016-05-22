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

// This library
import com.snowplowanalytics.iglu.core._

/**
 * [[ExtractFrom]] subtrait for [[JValue]].
 * It can be instantiated or extended by [[AttachTo]] instances
 */
trait ExtractFromData extends ExtractFrom[JValue] {

  /**
   * Extract [[SchemaKey]] from [[JValue]] data isntance
   * @param entity self-describing [[JValue]] data instance
   * @return [[SchemaKey]] if [[JValue]] was self-describing
   */
  def extractSchemaKey(entity: JValue): Option[SchemaKey] =
    entity \ "schema" match {
      case JString(schema) => SchemaKey.fromUri(schema)
      case _               => None
    }
}

/**
 * [[ExtractFrom]] type class instance, by importing this and making
 * implicit you'll have `extractSchemaKey` postfix method to extract
 * [[SchemaKey]] from data instances.
 */
object ExtractFromData extends ExtractFromData
