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
package com.snowplowanalytics.iglu.ctl

import java.nio.file.Path

import scala.annotation.tailrec

import cats.arrow.FunctionK
import cats.{Foldable, Show}
import cats.data.{EitherNel, EitherT, NonEmptyList}
import cats.effect.IO
import cats.implicits._

import fs2.Stream

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaMap, SchemaVer}


object Common {

  val liftIO: FunctionK[IO, Failing] = new FunctionK[IO, Failing] {
    def apply[A](fa: IO[A]): Failing[A] = EitherT.liftF(fa)
  }

  def liftEither[A](e: Either[Error, A]): Stream[Failing[?], A] =
    Stream.eval(EitherT.fromEither[IO](e))

  sealed trait GapError extends Product with Serializable {
    def show: String = this match {
      case GapError.NotInitSingle(schemaMap) =>
        s"Schema [${schemaMap.schemaKey.toPath}] contains a schema whose version is not 1-0-0"
      case GapError.InitMissing(vendor, name) =>
        s"Schemas of [$vendor/$name] do not contain initial 1-0-0 schema"
      case GapError.Gaps(vendor, name) =>
        s"Schemas of [$vendor/$name] have gaps between schema versions"
    }
  }

  object GapError {
    case class NotInitSingle(schemaMap: SchemaMap) extends GapError
    case class InitMissing(vendor: String, name: String) extends GapError
    case class Gaps(vendor: String, name: String) extends GapError

    implicit val show: Show[GapError] = Show.show(_.show)
  }

  def combineErrors[F[_]: Foldable, A: Show](errors: F[A]): String =
    errors.mkString_("", "\n", "")

  sealed trait Error extends Product with Serializable
  object Error {
    case class ConsistencyError(gapErrors: GapError) extends Error
    case class ReadError(path: Path, reason: String) extends Error
    case class ParseError(path: Path, reason: String) extends Error
    case class ConfigParseError(reason: String) extends Error
    case class PathMismatch(path: Path, schemaMap: SchemaMap) extends Error
    case class Message(line: String) extends Error
    case class ServiceError(line: String) extends Error
    case class WriteError(path: Path, reason: String) extends Error

    implicit val show: Show[Error] = Show.show {
      case Error.ConsistencyError(gapErrors) => gapErrors.show
      case Error.ReadError(path, reason) => s"Cannot read [$path]: $reason"
      case Error.ParseError(path, reason) => s"Cannot parse [$path]: $reason"
      case Error.ConfigParseError(reason) => s"Configuration is invalid: $reason"
      case Error.PathMismatch(path, schemaMap) => s"JSON schema in [$path] does not correspond to its metadata [${schemaMap.schemaKey.toSchemaUri}]"
      case Error.Message(line) => line
      case Error.ServiceError(line) => line
      case Error.WriteError(path, reason) => s"Cannot write [$path]: $reason"
    }
  }


  /**
    * Checks if there is any missing schema version in a directory of schemas
    * or if a specific schema file doesn't have version 1-0-0
    *
    * @param schemas list of valid JSON Schemas including all Self-describing information
    * @return (versionWarnings, versionErrors)
    */
  def checkSchemasConsistency(schemas: NonEmptyList[SchemaMap]): EitherNel[GapError, Unit] = {
    schemas match {
      case NonEmptyList(single, Nil) if single.schemaKey.version == SchemaVer.Full(1, 0, 0) => ().asRight
      case NonEmptyList(single, Nil) => NonEmptyList.of(GapError.NotInitSingle(single)).asLeft
      case many =>
        val schemaMapsGroupByVendor = many.map(_.schemaKey).groupBy(_.vendor)
        val versionErrors =
          for  {
            (_, schemaMapsOfVendor) <- schemaMapsGroupByVendor.toList
          } yield findGaps(schemaMapsOfVendor)
        versionErrors.flatten match {
          case Nil => ().asRight
          case h :: t => NonEmptyList(h, t).asLeft
        }
    }
  }

  private def findGaps(schemaMapsOfVendor: NonEmptyList[SchemaKey]): List[GapError] = {
    val schemaMapsGroupByName = schemaMapsOfVendor.groupBy(_.name)

    val firstVersionNotFoundErrors =
      for {
        (name, schemaMaps) <- schemaMapsGroupByName.toList
        if !schemaMaps.exists(_.version == SchemaVer.Full(1, 0, 0))
      } yield GapError.InitMissing(schemaMapsOfVendor.head.vendor, name)

    val schemaVerGapErrors =
      for {
        (name, schemaMaps) <- schemaMapsGroupByName.toList
        sortedSchemaMaps = schemaMaps.toList.sortWith(_.version.asString < _.version.asString)
        if sortedSchemaMaps.head.version == SchemaVer.Full(1, 0, 0) && existMissingSchemaVersion(sortedSchemaMaps)
      } yield GapError.Gaps(schemaMaps.head.vendor, name)

    firstVersionNotFoundErrors ::: schemaVerGapErrors
  }

  @tailrec
  def existMissingSchemaVersion(schemaMaps: List[SchemaKey]): Boolean = {
    val numOfMaps = schemaMaps.length

    if (numOfMaps == 1) false
    else {
      val prevModel    = schemaMaps.head.version.model
      val prevRevision = schemaMaps.head.version.revision
      val prevAddition = schemaMaps.head.version.addition
      val curModel     = schemaMaps.tail.head.version.model
      val curRevision  = schemaMaps.tail.head.version.revision
      val curAddition  = schemaMaps.tail.head.version.addition

      if (curModel == prevModel && curRevision == prevRevision && curAddition == prevAddition + 1 ||
        curModel == prevModel && curRevision == prevRevision + 1 && curAddition == 0 ||
        curModel == prevModel + 1 && curRevision == 0 && curAddition == 0)
        existMissingSchemaVersion(schemaMaps.tail) else true
    }
  }
}
