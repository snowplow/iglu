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
package com.snowplowanalytics.iglu.schemaddl.bigquery

import io.circe._

sealed trait CastError extends Product with Serializable

object CastError {
  /** Value doesn't match expected type */
  final case class WrongType(value: Json, expected: Type) extends CastError
  /** Field should be repeatable, but value is not an JSON Array */
  final case class NotAnArray(value: Json, expected: Type) extends CastError
  /** Value is required by Schema, but missing in JSON object */
  final case class MissingInValue(key: String, value: Json) extends CastError
}

