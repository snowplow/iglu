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

import cats.syntax.either._

// Circe
import io.circe._

// This library
import com.snowplowanalytics.iglu.core._

/**
 * [[ExtractFrom]] type class instance for JSON Schemas, stored in [[Json]].
 * By importing this and making implicit you'll have
 * `extractSchemaKey` postfix method to extract [[SchemaKey]] from JSON Schemas.
 */
trait ExtractFromSchema extends ExtractFrom[Json] {

  implicit val schemaKeyDecoder = CirceIgluCodecs.decodeSchemaKey

  /**
   * Extract SchemaKey using serialization formats defined at [[CirceIgluCodecs]]
   */
  def extractSchemaKey(entity: Json): Option[SchemaKey] = for {
    jsonObject <- entity.asObject
    selfJson   <- jsonObject.toMap.get("self")
    schemaKey  <- selfJson.as[SchemaKey].toOption
  } yield schemaKey
}

/**
 * [[ExtractFrom]] type class instance, by importing this and making
 * implicit you'll have `extractSchemaKey` postfix method to extract
 * [[SchemaKey]] from JSON Schemas
 */
object ExtractFromSchema extends ExtractFromSchema
