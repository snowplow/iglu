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
package typeclasses

/**
 * This type class can be instantiated for any type `E` which can bear its
 * description as [[SchemaKey]]
 *
 * Unlike [[AttachSchemaKey]] this type class makes possible to *only* extract
 * description from entity. Also it doesn't assume that type `E` *with*
 * description is the same `E` without it, so it can be instantiated for not
 * only JSON-like structures, but for case classes as well.
 * But in most cases these type classes can be instantiated for same types.
 *
 * It particularly useful for validation/data extraction apps which need to
 * *extract* instance/schema description.
 *
 * @tparam E entity type, mostly intended for various JSON ADTs,
 *           like Json4s, Jackson, circe, Argonaut etc,
 *           but also can be anything that can bear reference to
 *           its description like Thrift, Map[String, String] etc
 */
trait ExtractSchemaMap[E] {
  /**
   * Try to extract [[SchemaKey]] from entity
   */
  def extractSchemaMap(entity: E): Option[SchemaMap]
}
