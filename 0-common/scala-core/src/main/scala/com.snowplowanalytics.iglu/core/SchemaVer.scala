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

/**
  * Class holding semantic version for Schema
  *
  * + `model` Schema MODEL, representing independent Schema
  * + `revision` Schema REVISION, representing backward-incompatible changes
  * + `addition` Schema ADDITION, representing backward-compatible changes
  */
sealed trait SchemaVer {
  def asString: String

  def getModel: Option[Int]
  def getRevision: Option[Int]
  def getAddition: Option[Int]
}

object SchemaVer {

  def apply(model: Int, revision: Int, addition: Int): SchemaVer =
    Full(model, revision, addition)

  /** Explicit, fully known version. Can be attached to both data and schema */
  final case class Full(model: Int, revision: Int, addition: Int) extends SchemaVer {
    def asString = s"$model-$revision-$addition"

    def getModel = Some(model)
    def getRevision = Some(revision)
    def getAddition = Some(addition)
  }

  /** Partially known version. Can be attached only to data, schema need to be looked-up or inferenced */
  final case class Partial(model: Option[Int], revision: Option[Int], addition: Option[Int]) extends SchemaVer {
    def asString = s"${model.getOrElse("?")}-${revision.getOrElse("?")}-${addition.getOrElse("?")}"

    def getModel = model
    def getRevision = revision
    def getAddition = addition
  }

  /**
   * Regular expression to validate or extract `Full` SchemaVer,
   * with known MODEL, REVISION, ADDITION
   * Disallow preceding zeros and MODEL to be equal 0
   */
  val schemaVerFullRegex = "^([1-9][0-9]*)-(0|[1-9][0-9]*)-(0|[1-9][0-9]*)$".r

  /**
    * Regular expression to validate or extract `Partial` SchemaVer,
    * with possible unknown MODEL, REVISION, ADDITION
    */
  val schemaVerPartialRegex = (
      "^([1-9][0-9]*|\\?)-" +          // MODEL (cannot start with zero)
      "((?:0|[1-9][0-9]*)|\\?)-" +    // REVISION
      "((?:0|[1-9][0-9]*)|\\?)$").r   // ADDITION

  /**
   * Default [[Ordering]] instance for [[SchemaVer]]
   * making initial Schemas first and latest Schemas last
   */
  implicit val ordering: Ordering[SchemaVer] =
    Ordering.by { (schemaVer: SchemaVer) =>
      (schemaVer.getModel, schemaVer.getRevision, schemaVer.getAddition)
    }

  implicit val orderingFull: Ordering[Full] =
    Ordering.by { (schemaVer: SchemaVer.Full) =>
      (schemaVer.model, schemaVer.revision, schemaVer.addition)
    }

  /** Extract the model, revision, and addition of the SchemaVer (possibly unknown) */
  def parse(version: String): Option[SchemaVer] =
    parseFull(version).orElse { version match {
      case schemaVerPartialRegex(IntString(m), IntString(r), IntString(a)) =>
        Some(SchemaVer.Partial(m, r, a))
    } }

  /** Extract the model, revision, and addition of the SchemaVer (always known) */
  def parseFull(version: String): Option[SchemaVer.Full] = version match {
    case schemaVerFullRegex(m, r, a) =>
      Some(SchemaVer.Full(m.toInt, r.toInt, a.toInt))
    case _ =>
      None
  }

  /**
   * Check if string is valid SchemaVer
   *
   * @param version string to be checked
   * @return true if string is valid SchemaVer
   */
  def isValid(version: String): Boolean =
    version.matches(schemaVerFullRegex.toString)

  private object IntString {
    def unapply(arg: String): Option[Option[Int]] =
      if (arg == "?") Some(None)
      else {
        try {
          Some(Some(arg.toInt))
        } catch {
          case _: NumberFormatException => None
        }
      }
  }
}
