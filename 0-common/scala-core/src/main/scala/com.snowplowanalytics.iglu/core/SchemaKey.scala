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
package com.snowplowanalytics.iglu
package core

import scala.util.matching.Regex

import typeclasses.ExtractSchemaKey

/**
 * Entity describing schema of data, Duality of `SchemaMap`
 * Unlike `PartialSchemaKey` it always has full known version
 */
final case class SchemaKey(
  vendor: String,
  name: String,
  format: String,
  version: SchemaVer.Full) {

  /**
   * Converts the SchemaKey back to an Iglu-format schema URI
   *
   * @return the SchemaKey as a Iglu-format schema URI
   */
  def toSchemaUri: String =
    s"iglu:$vendor/$name/$format/${version.asString}"

  /** Lossy conversion to partial schema key */
  def asPartial: PartialSchemaKey =
    PartialSchemaKey(vendor, name, format, version)
}

/**
 * Companion object contains a custom constructor for
 * an Iglu SchemaKey.
 */
object SchemaKey {

  /** Canonical regular expression for SchemaKey */
  val schemaUriRegex: Regex = (
    "^iglu:" +                          // Protocol
    "([a-zA-Z0-9-_.]+)/" +              // Vendor
    "([a-zA-Z0-9-_]+)/" +               // Name
    "([a-zA-Z0-9-_]+)/" +               // Format
    "([1-9][0-9]*" +                    // MODEL (cannot start with 0)
    "(?:-(?:0|[1-9][0-9]*)){2})$").r    // REVISION and ADDITION
                                        // Extract whole SchemaVer within single group

  /**
   * Default `Ordering` instance for [[SchemaKey]]
   * Sort keys alphabetically AND by ascending SchemaVer
   * (so initial Schemas will be in the beginning)
   *
   * Usage:
   * {{{
   *   import com.snowplowanalytics.iglu.core.SchemaKey
   *   implicit val schemaOrdering = SchemaKey.ordering
   *   keys.sorted
   * }}}
   */
  val ordering: Ordering[SchemaKey] =
    Ordering.by { (key: SchemaKey) =>
      (key.vendor, key.name, key.format, key.version)
    }

  /**
   * Custom constructor for an Iglu SchemaKey from
   * an Iglu-format schema URI, which looks like:
   * iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0
   * Default for Schema reference
   *
   * @param schemaUri an Iglu-format Schema URI
   * @return a Validation-boxed SchemaKey for
   *         Success, and an error String on Failure
   */
  def fromUri(schemaUri: String): Option[SchemaKey] = schemaUri match {
    case schemaUriRegex(vnd, n, f, ver) =>
      SchemaVer.parse(ver).flatMap {
        case full: SchemaVer.Full => Some(SchemaKey(vnd, n, f, full))
        case _ => None
      }
    case _ => None
  }

  /** Try to decode `E` as `SchemaKey[E]` */
  def parse[E: ExtractSchemaKey](e: E): Option[SchemaKey] =
    implicitly[ExtractSchemaKey[E]].extractSchemaKey(e)
}
