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

/**
 * The four elements of any Iglu-compatible schema
 * SchemaKey can be used both for Self-describing instances
 * and Self-describing Schemas
 */
case class SchemaKey(
  vendor: String,
  name: String,
  format: String,
  version: SchemaVer) {

  /**
   * Converts a SchemaKey into a path which is compatible
   * with most local and remote Iglu schema repositories.
   *
   * @return a path usable for addressing local and remote
   *         Iglu schema lookups
   */
  def toPath: String =
    s"$vendor/$name/$format/${version.asString}"

  /**
   * Converts the SchemaKey back to an Iglu-format schema URI
   *
   * @return the SchemaKey as a Iglu-format schema URI
   */
  def toSchemaUri: String =
    s"iglu:$toPath"
}

/**
 * Companion object contains a custom constructor for
 * an Iglu SchemaKey.
 */
object SchemaKey {

  /**
   * Canonical regular expression for SchemaKey
   */
  val schemaUriRegex = (
    "^iglu:" +                          // Protocol
    "([a-zA-Z0-9-_.]+)/" +              // Vendor
    "([a-zA-Z0-9-_]+)/" +               // Name
    "([a-zA-Z0-9-_]+)/" +               // Format
    "([1-9][0-9]*" +                    // MODEL (cannot start with 0)
    "(?:-(?:0|[1-9][0-9]*)){2})$").r    // REVISION and ADDITION
                                        // Extract whole SchemaVer within single group

  /**
   * Regular expression to extract SchemaKey from path
   */
  val schemaPathRegex = (
    "^([a-zA-Z0-9-_.]+)/" +
    "([a-zA-Z0-9-_]+)/" +
    "([a-zA-Z0-9-_]+)/" +
    "([1-9][0-9]*" +
    "(?:-(?:0|[1-9][0-9]*)){2})$").r

  implicit val versionOrdering: Ordering[SchemaVer] = SchemaVer.ordering

  /**
   * Default [[Ordering]] instance for [[SchemaKey]]
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
  def fromUri(schemaUri: String): Option[SchemaKey] = schemaUri match {
    case schemaUriRegex(vnd, n, f, ver) =>
      SchemaVer.parse(ver).map(SchemaKey(vnd, n, f, _))
    case _ => None
  }

  /**
   * Custom constructor for an Iglu SchemaKey from
   * an Iglu-format Schema path, which looks like:
   * com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0
   * Can be used get Schema key by path on file system
   *
   * @param schemaPath an Iglu-format Schema path
   * @return a Validation-boxed SchemaKey for
   *         Success, and an error String on Failure
   */
  def fromPath(schemaPath: String): Option[SchemaKey] = schemaPath match {
    case schemaPathRegex(vnd, n, f, ver) =>
      SchemaVer.parse(ver).map(SchemaKey(vnd, n, f, _))
    case _ => None
  }
}
