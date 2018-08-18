/*
 * Copyright (c) 2012-2017 Snowplow Analytics Ltd. All rights reserved.
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

import typeclasses.{NormalizeSchema, StringifySchema, ToSchema}

/**
  * Container for Self-describing Schema
  * Used to eliminate need of Option container when extracting
  * [[SchemaMap]] with `ExtractSchemaMap` type class
  *
  * @param self Schema description
  * @param schema attached Schema instance itself
  * @tparam S generic type to represent Schema type (usually it is
  *           some JSON-library's base trait)
  */
final case class SelfDescribingSchema[S](self: SchemaMap, schema: S) {
  /**
    * Render Schema to its base type [[S]]
    */
  def normalize(implicit ev: NormalizeSchema[S]): S = ev.normalize(this)

  /**
    * Render Schema as [[String]]
    */
  def asString(implicit ev: StringifySchema[S]): String = ev.asString(this)
}

object SelfDescribingSchema {
  /** Try to decode `S` as `SelfDescribingSchema[S]` */
  def parse[S](schema: S)(implicit ev: ToSchema[S]): Option[SelfDescribingSchema[S]] =
    ev.toSchema(schema)
}

