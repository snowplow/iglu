/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu
package core

// Scala
import scala.language.implicitConversions

/**
 * Type-class representing essence of Self-describing data
 * It can be instantiated for any type [[E]] which is potentially
 * containing its own description (as [[SchemaKey]])
 *
 * @tparam E entity type, mostly intended for various JSON ADTs,
 *           like Json4s, Jackson, circe, Argonaut etc,
 *           but also can be anything that can bear reference to
 *           its description like Thrift, Map[String, String] etc
 */
trait SelfDescribed[E] {
  /**
   * Try to extract [[SchemaKey]] from entity
   */
  def getSchemaKey(entity: E): Option[SchemaKey]
}

object SelfDescribed {
  /**
   * Ops trait, adding postfix parameterless `getSchemaKey` method
   * to all types with instance of [[SelfDescribed]] type class
   *
   * @tparam E entity type supposed to be able to extract [[SchemaKey]]
   */
  trait SelfDescribedOps[E] {
    def self: E
    def getSchemaKey: Option[SchemaKey]
  }

  /**
   * Implicit conversion to [[SelfDescribedOps]] trait
   *
   * @param entity original entity bearing Schema key
   * @param ev implicit evidence entity type *can* bear Schema key
   * @tparam E entity type supposed to be able to extract [[SchemaKey]]
   * @return entity wrapped with Ops trait
   */
  implicit def toSelfDescribingOps[E](entity: E)(implicit ev: SelfDescribed[E]): SelfDescribedOps[E] =
    new SelfDescribedOps[E] {
      def self: E = entity
      def getSchemaKey: Option[SchemaKey] = ev.getSchemaKey(self)
    }
}
