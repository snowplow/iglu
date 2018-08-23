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

import typeclasses.ExtractSchemaMap

/**
  * Entity describing a schema object itself
  * Has known `SchemaVer`, extracted from `self` Schema's subobject
  * Duality of `SchemaKey`
  */
final case class SchemaMap(
  vendor: String,
  name: String,
  format: String,
  version: SchemaVer.Full) {

  /**
    * Converts a SchemaMap into a path which is compatible
    * with most local and remote Iglu schema repositories.
    *
    * @return a path usable for addressing local and remote
    *         Iglu schema lookups
    */
  def toPath: String =
    s"$vendor/$name/$format/${version.asString}"

  /** Convert the SchemaMap back to an Iglu-format schema URI */
  def toSchemaUri: String =
    s"iglu:$vendor/$name/$format/${version.asString}"

  /** Convert to its data-duality - `SchemaKey` */
  def toSchemaKey: SchemaKey =
    SchemaKey(vendor, name, format, version)
}

object SchemaMap {

  /** Canonical regular expression for SchemaKey/SchemaMap */
  val schemaUriRegex = (
    "^iglu:" +                          // Protocol
      "([a-zA-Z0-9-_.]+)/" +            // Vendor
      "([a-zA-Z0-9-_]+)/" +             // Name
      "([a-zA-Z0-9-_]+)/" +             // Format
      "([1-9][0-9]*" +                  // MODEL (cannot start with 0)
      "(?:-(?:0|[1-9][0-9]*)){2})$").r  // REVISION and ADDITION
                                        // Extract whole SchemaVer within single group

  /** Regular expression to extract SchemaKey from path */
  val schemaPathRegex = (
    "^([a-zA-Z0-9-_.]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([1-9][0-9]*" +
      "(?:-(?:0|[1-9][0-9]*)){2})$").r

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
  val ordering = Ordering.by { (key: SchemaKey) =>
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
  def fromUri(schemaUri: String): Option[SchemaMap] = schemaUri match {
    case schemaUriRegex(vnd, n, f, ver) =>
      SchemaVer.parseFull(ver).map(SchemaMap(vnd, n, f, _))
    case _ => None
  }

  /**
    * Custom constructor for an Iglu SchemaMap from
    * an Iglu-format Schema path, which looks like:
    * com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0
    * Can be used get Schema key by path on file system
    *
    * @param schemaPath an Iglu-format Schema path
    * @return some SchemaMap for Success, and none for failure
    */
  def fromPath(schemaPath: String): Option[SchemaMap] = schemaPath match {
    case schemaPathRegex(vnd, n, f, ver) =>
      SchemaVer.parse(ver).flatMap {
        case v: SchemaVer.Full => Some(SchemaMap(vnd, n, f, v))
        case _: SchemaVer.Partial => None   // Partial SchemaVer cannot be used
      }
    case _ => None
  }

  /** Try to decode `E` as `SchemaMap[E]` */
  def parse[E: ExtractSchemaMap](e: E): Option[SchemaMap] =
    implicitly[ExtractSchemaMap[E]].extractSchemaMap(e)

}
