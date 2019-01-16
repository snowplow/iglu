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

import scala.util.matching.Regex

/**
 * Entity describing schema of data, which **can** be unknown,
 * by known or unknown `SchemaVer`. Extracted from `schema` key.
 */
final case class PartialSchemaKey(
  vendor: String,
  name: String,
  format: String,
  version: SchemaVer) {

  /**
   * Converts the SchemaKey back to an Iglu-format schema URI
   *
   * @return the SchemaKey as a Iglu-format schema URI
   */
  def toSchemaUri: String =
    s"iglu:$vendor/$name/$format/${version.asString}"

  /** Transform to fully known `SchemaKey` */
  def toSchemaKey: Option[SchemaKey] =
    version match {
      case full: SchemaVer.Full =>
        Some(SchemaKey(vendor, name, format, full))
      case _ => None
    }
}

object PartialSchemaKey {

  /** Canonical regular expression for SchemaKey */
  val schemaUriRegex: Regex = (
    "^iglu:" +                            // Protocol
      "([a-zA-Z0-9-_.]+)/" +              // Vendor
      "([a-zA-Z0-9-_]+)/" +               // Name
      "([a-zA-Z0-9-_]+)/" +               // Format
      "([1-9][0-9]*|\\?)-" +              // MODEL (cannot start with zero)
      "((?:0|[1-9][0-9]*)|\\?)-" +        // REVISION
      "((?:0|[1-9][0-9]*)|\\?)").r        // ADDITION

  /**
    * Custom constructor for an Iglu partial SchemaKey from
    * an Iglu-format schema URI, which looks like:
    * iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0 for full or
    * iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-?-? for partial
    * Default for Schema reference
    *
    * @param schemaUri an Iglu-format Schema URI
    * @return some (possibly partial) schema key if `schemaUri` was valid
    */
  def fromUri(schemaUri: String): Either[ParseError, PartialSchemaKey] = schemaUri match {
    case schemaUriRegex(vnd, n, f, m, r, a) =>
      SchemaVer.parse(s"$m-$r-$a").right.map(PartialSchemaKey(vnd, n, f, _))
    case _ => Left(ParseError.InvalidIgluUri)
  }
}


