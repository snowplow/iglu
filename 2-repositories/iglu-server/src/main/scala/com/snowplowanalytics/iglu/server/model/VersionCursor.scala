/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.iglu.server.model

import cats.Show
import cats.syntax.either._

import com.snowplowanalytics.iglu.core.SchemaVer

/** ADT representing a place of schema in SchemaVer tree */
sealed trait VersionCursor extends Product with Serializable

object VersionCursor {
  /** The very first schema of whole group */
  case object Initial extends VersionCursor
  /** First schema of a particular model */
  case class StartModel private(model: Int) extends VersionCursor
  /** First schema (addition) of a particular revision of particular model */
  case class StartRevision private(model: Int, revision: Int) extends VersionCursor
  /** Non-initial schema */
  case class NonInitial private(schemaVer: SchemaVer.Full) extends VersionCursor

  sealed trait Inconsistency extends Product with Serializable
  object Inconsistency {
    case object PreviousMissing extends Inconsistency
    case object AlreadyExists extends Inconsistency
    case class Availability(isPublic: Boolean, previousPublic: Boolean) extends Inconsistency

    implicit val inconsistencyShowInstance: Show[Inconsistency] =
      Show.show {
        case PreviousMissing =>
          "Preceding SchemaVer in the group is missing, check that schemas published in proper order"
        case AlreadyExists =>
          "Schema already exists"
        case Availability(isPublic, previousPublic) =>
          s"Inconsistent schema availability. Cannot add ${if (isPublic) "public" else "private"} schema, previous versions are ${if (previousPublic) "public" else "private"}"
      }
  }

  def isAllowed(version: SchemaVer.Full, existing: List[SchemaVer.Full], patchesAllowed: Boolean): Either[Inconsistency, Unit] =
    if (existing.contains(version) && !patchesAllowed) Inconsistency.AlreadyExists.asLeft
    else if (previousExists(existing, get(version))) ().asRight else Inconsistency.PreviousMissing.asLeft

  def get(version: SchemaVer.Full): VersionCursor = version match {
    case SchemaVer.Full(1, 0, 0) => Initial
    case SchemaVer.Full(m, 0, 0) => StartModel(m)
    case SchemaVer.Full(m, r, 0) => StartRevision(m, r)
    case next => NonInitial(next)
  }

  /**
    * Check if existing state allows new schema
    * It makes an assumption that `existing` is entirely consistent list without `current` schema
    */
  private[model] def previousExists(existing: List[SchemaVer.Full], current: VersionCursor) =
    current match {
      case Initial => // We can always create a new schema group (vendor/name/1-0-0)
        true
      case StartModel(m) =>
        existing.map(_.model).contains(m - 1)
      case StartRevision(m, r) =>
        val thisModel = existing.filter(_.model == m)
        thisModel.map(_.revision).contains(r - 1)
      case NonInitial(version) =>
        val thisModel = existing.filter(_.model == version.model)
        val thisRevision = thisModel.filter(_.revision == version.revision)
        thisRevision.map(_.addition).contains(version.addition - 1)
    }
}

