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

import typeclasses.{ NormalizeData, StringifyData, ToData }

/**
  * Container for Self-describing data
  * Used to eliminate need of Option container when extracting
  * `SchemaKey` with `ExtractSchemaKey` type class
  *
  * @param data reference to Schema
  * @param schema attached data instance itself
  * @tparam D generic type to represent data instance type
  *           (usually it is some JSON-library's base trait)
  */
case class SelfDescribingData[D](schema: SchemaKey, data: D) {
  /**
    * Render data instance to its base type [[D]]
    */
  def normalize(implicit ev: NormalizeData[D]): D = ev.normalize(this)

  /**
    * Render data instance as [[String]]
    */
  def asString(implicit ev: StringifyData[D]): String = ev.asString(this)
}

object SelfDescribingData {
  def parse[D](data: D)(implicit ev: ToData[D]): Option[SelfDescribingData[D]] =
    ev.toData(data)
}
