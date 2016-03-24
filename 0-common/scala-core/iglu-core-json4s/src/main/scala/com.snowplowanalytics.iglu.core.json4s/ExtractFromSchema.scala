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
import  com.snowplowanalytics.iglu.core._

/**
 * Example common trait for [[ExtractFrom]] *Schemas* objects
 * It can be instantiated or extended by [[AttachTo]]
 */
trait ExtractFromSchema extends ExtractFrom[JValue] {

  implicit val formats: Formats = Json4sIgluCodecs.formats

  /**
   * Extract SchemaKey using serialization formats defined at [[Json4sIgluCodecs]]
   */
  def extractSchemaKey(entity: JValue): Option[SchemaKey] =
    (entity \ "self").extractOpt[SchemaKey]
}

object ExtractFromSchema extends ExtractFromSchema
