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

/**
 * Module defining common containers for Self-describing entities:
 * Self-describing Schemas and Self-describing data
 *
 * Containers add type-safety to type class approach (with [[ExtractFrom]]
 * and [[AttachTo]]) as it is very hard to distinguish what was placed
 * inside JSON, is it a Schema or instance and also how to represent it correctly.
 * For latter task `Normalize*` type-classes exist.
 */
object Containers {

  /**
   * Container for Self-describing Schema
   * Used to eliminate need of Option container when extracting
   * [[SchemaKey]] with [[ExtractFrom]] type class
   *
   * @param self Schema description
   * @param schema attached Schema instance itself
   * @tparam S generic type to represent Schema type (usually it is
   *           some JSON-library's base trait)
   */
  case class SelfDescribingSchema[S](self: SchemaKey, schema: S) {
    /**
     * Render Schema to its base type [[S]]
     */
    def normalize(implicit ev: NormalizeSchema[S]): S = ev.normalize(this)

    /**
     * Render Schema as [[String]]
     */
    def asString(implicit ev: StringifySchema[S]): String = ev.asString(this)
  }

  /**
   * Container for Self-describing data
   * Used to eliminate need of Option container when extracting
   * [[SchemaKey]] with [[ExtractFrom]] type class
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

  /**
   * Type class to render Schema into its base type [[S]]
   * and lowest-level ([[String]]) common for all aps
   *
   * @tparam S generic type in which Schema can be represented
   */
  trait NormalizeSchema[S] {
    /**
     * Render Schema to its base type [[S]]
     */
    def normalize(container: SelfDescribingSchema[S]): S
  }

  /**
   * Type class to render data into it base type [[D]]
   * and lowest-level ([[String]]) common for all aps
   *
   * @tparam D generic type in which instance can be represented
   */
  trait NormalizeData[D] {
    /**
     * Render data instance to its base type [[D]]
     */
    def normalize(container: SelfDescribingData[D]): D
  }

  /**
   * Type class to render container with Schema to [[String]]
   *
   * @tparam S generic type in which Schema can be represented
   */
  trait StringifySchema[S] {
    /**
     * Render Schema as [[String]]
     */
    def asString(container: SelfDescribingSchema[S]): String
  }

  /**
   * Type class to render container with Schema to [[String]]
   *
   * @tparam D generic type in which data instance can be represented
   */
  trait StringifyData[D] {
    /**
     * Render data instance as [[String]]
     */
    def asString(container: SelfDescribingData[D]): String
  }

  /**
   * Mixin for [[AttachTo]] marking that this particular instance of
   * [[AttachTo]] intended for extraction data, not Schemas
   */
  trait ToData[E] extends ExtractContent[E] { self: AttachTo[E] =>
    def toData(entity: E): Option[SelfDescribingData[E]] =
      self.getContentPair(entity).map { case (key, data) =>
        SelfDescribingData(key, data)
      }
  }

  /**
   * Ops methods for [[ToData]] type class
   */
  implicit class ToDataOps[E: ToData](instance: E) {
    def toData: Option[SelfDescribingData[E]] =
      implicitly[ToData[E]].toData(instance)
  }

  /**
   * Mixin for [[AttachTo]] marking that this particular instance intended
   * for extraction Schemas, not instances
   */
  trait ToSchema[E] extends ExtractContent[E] { self: AttachTo[E] =>
    def toSchema(schema: E): Option[SelfDescribingSchema[E]] =
      self.getContentPair(schema).map { case (selfKey, schemaContent) =>
        SelfDescribingSchema(selfKey, schemaContent)
      }
  }

  /**
   * Ops methods for [[ToSchema]] type class
   */
  implicit class ToSchemaOps[E: ToSchema](schema: E) {
    def toSchema: Option[SelfDescribingSchema[E]] =
      implicitly[ToSchema[E]].toSchema(schema)
  }

  /**
   * This is auxiliary trait. By all means its logic (getContent) can be
   * implemented as part of [[AttachTo]], but its defined as separate
   * trait to reduce boilerplate code for those who don't need to implement
   * [[ToData]] and [[ToSchema]]
   *
   * @tparam E type of content to be extracted, can be both schema and data itself
   */
  trait ExtractContent[E] { self: AttachTo[E] =>
    /**
     * Get only content of entity, without any data related to Iglu
     *
     * @param entity full entity, probably with [[SchemaKey]]
     * @return entity without [[SchemaKey]]
     */
    def getContent(entity: E): Option[E]

    /**
     * Try to get pair of [[SchemaKey]] and entity. Further it can be
     * placed in appropriate container ([[SelfDescribingSchema]]
     * or [[SelfDescribingData]])
     *
     * @param entity full entity, probably with [[SchemaKey]]
     * @return optional pair of [[SchemaKey]] and [[E]]
     */
    def getContentPair(entity: E): Option[(SchemaKey, E)] =
      for {
        key     <- extractSchemaKey(entity)
        content <- getContent(entity)
      } yield (key, content)
  }
}
