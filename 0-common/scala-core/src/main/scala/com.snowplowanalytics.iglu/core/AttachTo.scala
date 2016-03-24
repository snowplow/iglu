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
package com.snowplowanalytics.iglu.core

// Scala
import scala.language.implicitConversions

/**
 * This type class can be instantiated for any type [[E]] which after
 * attaching [[SchemaKey]] to it will remain same [[E]] (like Self-describing
 * JSON - same `JValue` before and after).
 *
 * Unlike [[ExtractFrom]] this type class makes possible also to attach
 * description to entity, not just extract it.
 *
 * Instance of this type class also need to decide *how* to attach
 * description to entity as for entities of same type [[SchemaKey]]
 * could be attached differently. For e.g. `schema` key for
 * Self-describing JSON data and `self` key for JSON Schema,
 * so different instances should be written for different purposes.
 *
 * This particularly useful for apps creating, sending or storing
 * Self-describing data (like trackers), or in other words to *attach*
 * description to data
 *
 * @tparam E entity type, mostly intended for various JSON ADTs,
 *           like Json4s, Jackson, circe, Argonaut etc,
 *           but also can be anything that can bear reference to
 *           its description and at the same time remain same type
 */
trait AttachTo[E] extends ExtractFrom[E] {
  /**
   * Attach (merge-in) [[SchemaKey]] into entity of type [[E]]
   *
   * @param schemaKey Schema description or reference to it
   * @param entity some entity that can be Self-described
   * @return updated entity with attached [[SchemaKey]]
   */
  def attachSchemaKey(schemaKey: SchemaKey, entity: E): E
}

object AttachTo {
  /**
   * Ops class, adding postfix `attachSchemaKey` method and its
   * alias `makeSelfDescribing` to all types with instance of
   * [[ExtractFrom]] type class
   *
   * @param entity original entity without Schema key
   * @tparam E entity type supposed to be able to merge-in [[SchemaKey]]
   */
  implicit class AttachToOps[E: AttachTo](entity: E) {
    def attachSchemaKey(schemaKey: SchemaKey): E =
      implicitly[AttachTo[E]].attachSchemaKey(schemaKey, entity)

    /**
     * Alias for [[attachSchemaKey]]
     */
    def makeSelfDescribing(schemaKey: SchemaKey): E =
      attachSchemaKey(schemaKey)
  }
}
