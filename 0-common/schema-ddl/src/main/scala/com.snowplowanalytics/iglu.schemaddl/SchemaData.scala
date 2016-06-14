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
package com.snowplowanalytics.iglu.schemaddl

// Scala
import scala.collection.immutable.ListMap

/**
 * Data structures for intermediate Schema representations
 */
object SchemaData {
  /**
   * Class containing all information taken from Self-describing Schema
   */
  case class SelfDescInfo(vendor: String, name: String, version: String, format: String = "jsonschema")

  /**
   * Flat schema container. Contains self-describing properties in ``self``
   * and all primitive types as ordered flatten map in ``elems``
   *
   * @param elems The ordered map of every primitive type in schema
   *              and it's unordered map of properties
   */
  case class FlatSchema(elems: ListMap[String, Map[String, String]], required: Set[String] = Set.empty[String])

}
