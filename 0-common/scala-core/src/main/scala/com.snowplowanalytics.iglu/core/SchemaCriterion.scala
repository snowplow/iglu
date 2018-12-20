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

import typeclasses.ExtractSchemaKey

/**
 * Class to filter Schemas by [[SchemaKey]]
 */
final case class SchemaCriterion(
  vendor: String,
  name: String,
  format: String,
  model: Option[Int] = None,
  revision: Option[Int] = None,
  addition: Option[Int] = None) {

  /**
   * Whether a SchemaKey is valid.
   *
   * It's valid if the vendor, name, format, and model all match
   * and the supplied key's revision and addition do not exceed the
   * criterion's revision and addition.
   *
   * @param key The SchemaKey to validate
   * @return whether the SchemaKey is valid
   */
  def matches(key: SchemaKey): Boolean =
    prefixMatches(key) && verMatch(key.version)

  /**
   * Filter sequence of entities by this [[SchemaCriterion]]
   * It can be applied for getting only right JSON instances
   * out of array with custom context
   *
   * Usage:
   * {{{
   *   // This will get best matching entity
   *   criterion.takeFrom(_.schema)(entities).sort.getOption
   * }}}
   *
   * @param entities list of Self-describing instances (or Schemas)
   * @tparam E type of Self-describing entity, having
   *           an `ExtractSchemaKey` instance in scope
   * @return list of matching entities
   */
  def pickFrom[E: ExtractSchemaKey](entities: Seq[E]): Seq[E] =
    entities.foldLeft(Seq.empty[E]) { (acc, cur) =>
      SchemaKey.extract(cur) match {
        case Right(key) if this.matches(key) => cur +: acc
        case _ => acc
      }
    }

  /**
   * Format as a schema URI, but the revision and addition
   * may be replaced with "*" wildcards.
   *
   * @return the String representation of this criterion
   */
  def asString: String =
    s"iglu:$vendor/$name/$format/$versionString"

  /**
   * Stringify version part of criterion
   */
  def versionString: String =
    "%s-%s-%s".format(model.getOrElse("*"), revision.getOrElse("*"), addition.getOrElse("*"))

  /**
   * Whether the vendor, name, and format are all correct.
   *
   * @param key The SchemaKey to validate
   * @return whether the first three fields are correct
   */
  private def prefixMatches(key: SchemaKey): Boolean =
    key.vendor == vendor && key.name == name && key.format == format

  /**
   * Match only [[SchemaVer]]
   *
   * @param ver SchemaVer of some other [[SchemaKey]]
   * @return true if all specified groups matched
   */
  private[this] def verMatch(ver: SchemaVer): Boolean = {
    groupMatch(ver.getModel, model) &&
      groupMatch(ver.getRevision, revision) &&
      groupMatch(ver.getAddition, addition)
  }

  /**
   * Helper function for [[verMatch]]. Compares two numbers for same group
   *
   * @param other Schema's SchemaVer group (MODEL, REVISION, ADDITION)
   * @param crit this Criterion's corresponding group
   * @return true if groups match or criterion not specific about it
   */
  private[this] def groupMatch(other: Option[Int], crit: Option[Int]): Boolean = crit match {
    case Some(c) if other == Some(c) => true
    case Some(_) if other.isEmpty => true
    case None => true
    case _ => false
  }
}

/**
 * Companion object containing alternative constructor for a [[SchemaCriterion]]
 */
object SchemaCriterion {

  /**
   * Canonical regex to extract Schema criterion
   */
  val criterionRegex = (
    "^iglu:" +                      // Protocol
    "([a-zA-Z0-9-_.]+)/" +          // Vendor
    "([a-zA-Z0-9-_]+)/" +           // Name
    "([a-zA-Z0-9-_]+)/" +           // Format
    "([1-9][0-9]*|\\*)-" +          // MODEL (cannot start with zero)
    "((?:0|[1-9][0-9]*)|\\*)-" +    // REVISION
    "((?:0|[1-9][0-9]*)|\\*)$").r   // ADDITION

  /**
   * Custom constructor for an SchemaCriterion from a string, like
   * iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-*-*
   *
   * @param criterion string supposed to be criterion
   * @return criterion object if string satisfies a format
   */
  def parse(criterion: String): Option[SchemaCriterion] = {
    criterion match {
      case criterionRegex(vendor, name, format, m, r, a) =>
        Some(SchemaCriterion(vendor, name, format, parseInt(m), parseInt(r), parseInt(a)))
      case _ => None
    }
  }

  /**
   * Constructs an exhaustive SchemaCriterion.
   *
   * @return our constructed SchemaCriterion
   */
  def apply(vendor: String, name: String, format: String, model: Int, revision: Int, addition: Int): SchemaCriterion =
    SchemaCriterion(vendor, name, format, Some(model), Some(revision), Some(addition))

  /**
   * Constructs a SchemaCriterion from everything
   * except the addition.
   *
   * @return our constructed SchemaCriterion
   */
  def apply(vendor: String, name: String, format: String, model: Int, revision: Int): SchemaCriterion =
    SchemaCriterion(vendor, name, format, Some(model), Some(revision))

  /**
   * Constructs a SchemaCriterion which is agnostic
   * of addition and revision.
   * Restricts to model only.
   *
   * @return our constructed SchemaCriterion
   */
  def apply(vendor: String, name: String, format: String, model: Int): SchemaCriterion =
    SchemaCriterion(vendor, name, format, Some(model), None, None)

  /**
   * Try to parse string number
   * Helper method for [[parse]]
   */
  private def parseInt(number: String): Option[Int] = {
    try {
      Some(number.toInt)
    } catch {
      case _: NumberFormatException => None
    }
  }
}
