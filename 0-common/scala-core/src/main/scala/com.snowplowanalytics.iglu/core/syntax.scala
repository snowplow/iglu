/*
 * Copyright (c) 2016-2017 Snowplow Analytics Ltd. All rights reserved.
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

import typeclasses._

trait syntax {
  /**
    * Ops class, adding postfix `attachSchemaKey` method and its
    * alias `makeSelfDescribing` to all types with instance of
    * `ExtractSchemaKey` type class
    *
    * @param entity original entity without Schema key
    * @tparam E entity type supposed to be able to merge-in [[SchemaKey]]
    */
  implicit class AttachToOps[E: AttachSchemaKey](entity: E) {
    def attachSchemaKey(schemaKey: SchemaKey): E =
      implicitly[AttachSchemaKey[E]].attachSchemaKey(schemaKey, entity)

    /** Alias for [[attachSchemaKey]] */
    def makeSelfDescribing(schemaKey: SchemaKey): E =
      attachSchemaKey(schemaKey)
  }

  /**
    * Ops class, adding postfix `attachSchemaMap` method and its
    * alias `makeSelfDescribing` to all types with instance of
    * `ExtractSchemaMap` type class
    *
    * @param entity original entity without Schema key
    * @tparam E entity type supposed to be able to merge-in [[SchemaKey]]
    */
  implicit class AttachSchemaMapOps[E: AttachSchemaMap](entity: E) {
    def attachSchemaMap(schemaMap: SchemaMap): E =
      implicitly[AttachSchemaMap[E]].attachSchemaMap(schemaMap, entity)

    /**
      * Alias for [[attachSchemaMap]]
      */
    def makeSelfDescribing(schemaMap: SchemaMap): E =
      attachSchemaMap(schemaMap)
  }

  /**
    * Ops class, adding postfix parameterless `extractSchemaKey` method
    * to all types with instance of `ExtractSchemaKey` type class
    *
    * @param entity original entity (Schema or instance) bearing [[SchemaKey]]
    * @tparam E entity type supposed to be able to extract [[SchemaKey]]
    */
  implicit class ExtractFromDataOps[E: ExtractSchemaKey](entity: E) {
    /**
      * Postfix method implementing [[getSchemaKey]]
      */
    def extractSchemaKey: Option[SchemaKey] =
      implicitly[ExtractSchemaKey[E]].extractSchemaKey(entity)

    /**
      * Alias for [[extractSchemaKey]]
      */
    def getSchemaKey: Option[SchemaKey] =
      extractSchemaKey

    /**
      * Unsafely extract [[SchemaKey]]. Must be used only on types where it
      * presented for sure, like case classes
      *
      * @return not-wrapped SchemaKey
      */
    def getSchemaKeyUnsafe: SchemaKey =
      try {
        extractSchemaKey.get
      } catch {
        case _: NoSuchElementException =>
          throw new RuntimeException(s"Cannot extract SchemaKey from object [$entity]")
      }
  }

  /**
    * Ops class, adding postfix parameterless `extractSchemaMap` method
    * to all types with instance of `ExtractSchemaMap` type class
    *
    * @param entity original entity (Schema or instance) bearing [[SchemaMap]]
    * @tparam E entity type supposed to be able to extract [[SchemaMap]]
    */
  implicit class ExtractFromSchemaOps[E: ExtractSchemaMap](entity: E) {
    /**
      * Postfix method implementing [[getSchemaMap]]
      */
    def extractSchemaMap: Option[SchemaMap] =
      implicitly[ExtractSchemaMap[E]].extractSchemaMap(entity)

    /**
      * Alias for `extractSchemaMap`
      */
    def getSchemaMap: Option[SchemaMap] =
      extractSchemaMap

    /**
      * Unsafely extract `SchemaMap`. Must be used only on types where it
      * presented for sure, like case classes
      *
      * @return not-wrapped SchemaMap
      */
    def getSchemaMapUnsafe: SchemaMap =
      try {
        extractSchemaMap.get
      } catch {
        case _: NoSuchElementException =>
          throw new RuntimeException(s"Cannot extract SchemaKey from object [$entity]")
      }
  }

  /**
    * Ops methods for `ToData` type class
    */
  implicit class ToDataOps[E: ToData](instance: E) {
    def toData: Option[SelfDescribingData[E]] =
      implicitly[ToData[E]].toData(instance)
  }

  /**
    * Ops methods for `ToSchema` type class
    */
  implicit class ToSchemaOps[E: ToSchema](schema: E) {
    def toSchema: Option[SelfDescribingSchema[E]] =
      implicitly[ToSchema[E]].toSchema(schema)
  }
}

object syntax extends syntax
