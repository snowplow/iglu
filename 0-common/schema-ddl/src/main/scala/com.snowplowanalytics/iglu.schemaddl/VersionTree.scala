/*
 * Copyright (c) 2016-2019 Snowplow Analytics Ltd. All rights reserved.
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

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.int._
import cats.instances.list._
import cats.syntax.functor._
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.reducible._

import com.snowplowanalytics.iglu.core.SchemaVer

import VersionTree._

/**
  * The order preserving tree, containing all versions and satisfying following properties:
  * - A version is _clustered_ with previous ones if higher group matches
  *   e.g. for 1-0-0 and 1-0-1 both higher groups (MODEL and REVISION) match
  *   e.g. for 1-0-1 and 1-1-0 only MODEL matches, so same MODEL cluster, but new REVISION cluster
  * - A version spawns a new cluster if previous higher group is either smaller or larger
  *   e.g. 1-0-0, 1-1-0, 1-0-1 is a valid version list, but has three separate REVISION clusters
  * - There's no gaps between versions (e.g. [1-0-0, 1-0-2] is impossible)
  * - Tree is non-empty and always starts with 1-0-0
  *
  * @param models list of MODEL clusters in reverse order (latest one is head)
  *               e.g. (1, [0-0, 0-1]), (2, [0-0]), (1, [0-2, 1-0])
  */
final case class VersionTree private(models: NonEmptyList[(Model, Revisions)]) extends AnyVal {
  // TODO: name all things consistently

  /** Get all versions in their natural order */
  def versionList: VersionTree.VersionList = {
    val list = for {
      (model, revisions) <- models.toList
      (revision, additions) <- revisions.revisions.toList
      addition <- additions.values.toList
    } yield SchemaVer.Full(model, revision, addition)

    VersionTree.VersionList(NonEmptyList.fromListUnsafe(list.reverse))
  }

  /** Try to add a next version to the tree, which can be rejected if any properties don't hold */
  def add(version: SchemaVer.Full): Either[AddingError, VersionTree] = {
    for {
      placement <- getSetPlacement(modelsSet, version.model, false)
      aggregatedAdditions = getAdditions(version.model, version.revision)
      revision <- placement match {
        case SetPlacement.ContinueCurrent =>
          NonEmptyList.fromList(getRevisions(version.model)) match {
            case Some(aggregated) =>
              latestRevision.add(aggregated, aggregatedAdditions, version).map { revisions =>
                VersionTree(NonEmptyList((version.model, revisions), this.models.tail))
              }
            case None =>
              throw new IllegalArgumentException(s"Invalid state of VersionTree, ${version.model} revisions cannot be empty in ${this}")
          }
        case SetPlacement.SpawnNext =>
          val revisionSet = NonEmptyList.fromList(getRevisions(version.model)) match {
            case Some(aggregated) => aggregated
            case None => NonEmptyList.of(version.revision)
          }
          for {
            additionGroup  <- Additions.spawn(aggregatedAdditions, version.addition) // just 1-0-x
            revisionsGroup  = Revisions(NonEmptyList.of((version.revision, additionGroup)))
            _              <- getSetPlacement(revisionSet, version.revision, true)
          } yield VersionTree((version.model, revisionsGroup) :: this.models)

      }
    } yield revision
  }

  def show: String = models.map { case (model, revisions) => s"+ $model\n${revisions.show}" }.toList.mkString("\n")

  /** Get all revisions for a particular `model` (duplicates are possible) */
  private def getRevisions(model: Model) =
    models
      .collect { case (m, group) if m == model => group }
      .flatMap(_.revisions.map(_._1).toList)

  /** Get all revisions for a particular `model` (duplicates are possible) */
  private def getAdditions(model: Model, revision: Revision) =
    models
      .toList
      .collect { case (m, group) if m == model => group }
      .flatMap { revisions => revisions.getAdditions(revision) }

  private def latestRevision = models.head._2
  private def modelsSet = models.map(_._1)
}


/**
  * Group - continuous, but possibly *unclosed* sequence of child versions (opposed to always closed Set?),
  * e.g. 2,3 additions of 0 revision (but 0,1,4,5 are "outside")
  *
  * Set - continuous and always closed sequence of child versions (opposed to possibly *unclosed* Group)
  * e.g. 0,1,2,3,4,5 additions of 0 revision (nothing else in the revision)
  *
  * Highest - largest number in a whole Set (5th addition)
  * Latest - largest number in a whole Group (3rd addition)
  *
  * case class X is a group, it has information about all its Xs and children Ys
  */
object VersionTree {

  type Model = Int
  type Revision = Int
  type Addition = Int

  /** List of consistent naturally ordered versions, entirely isomorphic to the original tree */
  case class VersionList private(versions: NonEmptyList[SchemaVer.Full]) extends AnyVal {
    def toTree: VersionTree =
      build(versions.toList.reverse)
        .toOption
        .getOrElse(throw new IllegalStateException(s"VersionList $versions is not isomorphic to the tree"))
  }

  /** A tree with only 1-0-0 */
  val Root = VersionTree(NonEmptyList.of(
    (1, Revisions(NonEmptyList.of(
      (0, Additions(
        NonEmptyList.of(0))
      ))
    )))
  )

  /** Error happened during tree building */
  sealed trait BuildingError
  object BuildingError {
    final case object EmptyTree extends BuildingError
    final case class InvalidInit(remaining: NonEmptyList[SchemaVer.Full]) extends BuildingError
    final case class InvalidTree(addingError: AddingError, tree: VersionTree, problem: SchemaVer.Full) extends BuildingError
  }

  /** Error happened during adding a particular version to the tree */
  sealed trait AddingError
  object AddingError {
    final case object AlreadyExists extends AddingError
    final case class AdditionGaps(elements: NonEmptyList[Addition]) extends AddingError
    final case class RevisionGaps(elements: NonEmptyList[Revision]) extends AddingError
    final case class ModelGaps(elements: NonEmptyList[Model]) extends AddingError
  }

  def build(versions: List[SchemaVer.Full]): Either[BuildingError, VersionTree] =
    versions match {
      case Nil => BuildingError.EmptyTree.asLeft
      case SchemaVer.Full(1,0,0) :: other =>
        other.foldLeft(Root.asRight[BuildingError]) { (acc, cur) =>
          acc match {
            case Right(tree) => tree.add(cur) match {
              case Right(result) => result.asRight
              case Left(error) => BuildingError.InvalidTree(error, tree, cur).asLeft
            }
            case Left(error) => error.asLeft

          }
        }
      case init :: other => BuildingError.InvalidInit(NonEmptyList(init, other)).asLeft
    }

  final case class Revisions private(revisions: NonEmptyList[(Revision, Additions)]) extends AnyVal {
    /**
      * Add `version` to this cluster. MODEL is irrelevant
      * @param revisionSet all REVISIONs in parent MODEL (`version.model`)
      *                    that can reside in other groups
      * @param additions all additions in this `MODEL-REVISION` group
      * @param version SchemaVer to add
      * @return updated REVISIONs cluster if version can be added, error otherwise
      */
    def add(revisionSet: NonEmptyList[Revision], additions: List[Addition], version: SchemaVer.Full): Either[AddingError, Revisions] = {
      for {
        positionInRevision <- getSetPlacement(revisionSet, version.revision, true)
        updated <- positionInRevision match {
          case SetPlacement.ContinueCurrent =>
            latestAddition.add(additions, version.addition).map { additionsGroup =>
              Revisions(NonEmptyList((version.revision, additionsGroup), revisions.tail))
            }
          case SetPlacement.SpawnNext =>
            for {
              additionGroups <- Additions.spawn(additions, version.addition)
            } yield Revisions((version.revision, additionGroups) :: this.revisions)
        }
      } yield updated
    }

    def show: String = revisions.map { case (rev, additions) => s" - $rev ${additions.show}" }.toList.mkString("\n")

    /** Return ADDITIONs only in current MODEL group */
    private[schemaddl] def getAdditions(revision: Int): List[Addition] =
      revisions // Unlike getRevisions it can be empty list
        .collect { case (r, group) if r == revision => group }
        .flatMap(_.value.values.toList)

    private def latestAddition = revisions.head._2
  }

  final case class Additions private(values: NonEmptyList[Addition]) extends AnyVal {
    /**
      * Add `addition` to `group`
      * @param aggregated additions across whole Set
      * @param addition version to add
      */
    private[schemaddl] def add(aggregated: List[Int], addition: Int): Either[AddingError, Additions] = {
      getAdditionPosition(aggregated, addition).as { Additions(addition :: values) }
    }

    def show: String = values.mkString_("[", ",", "]")
  }

  object Additions {
    private[schemaddl] def spawn(additionSet: List[Addition], addition: Int): Either[AddingError, Additions] =
      additionSet match {
        case Nil if addition == 0 => Additions(NonEmptyList.of(addition)).asRight
        case list =>
          getAdditionPosition(list, addition).as(Additions(NonEmptyList.of(addition)))
      }
  }

  private[schemaddl] sealed trait SetPlacement
  private[schemaddl] object SetPlacement {
    /** Latest version is smaller, need to spawn new cluster */
    case object SpawnNext extends SetPlacement
    /** Latest version is what we're looking for, need to continue the cluster */
    case object ContinueCurrent extends SetPlacement
  }

  /** Check if there are any gaps in `set` (MODEL or REVISION) */
  private[schemaddl] def getSetPlacement(set: NonEmptyList[Int], versionNumber: Int, zeroBased: Boolean): Either[AddingError, SetPlacement] = {
    val placement = if (set.head == versionNumber) SetPlacement.ContinueCurrent else SetPlacement.SpawnNext
    val check = gapCheck(versionNumber :: set, zeroBased).as(placement)
    check.leftMap { gaps => if (zeroBased) AddingError.RevisionGaps(gaps) else AddingError.ModelGaps(gaps) }
  }

  private[schemaddl] def getAdditionPosition(set: List[Int], addition: Int): Either[AddingError, Unit] =
    for {
      _ <- gapCheck(NonEmptyList(addition, set), true).leftMap(AddingError.AdditionGaps.apply)
      result <- if (addition == set.maximumOption.getOrElse(0) + 1) ().asRight else AddingError.AlreadyExists.asLeft
    } yield result

  private def gapCheck(elements: NonEmptyList[Int], zeroBased: Boolean) = {
    val start = if (zeroBased) 0 else 1
    val max = elements.maximum
    val diff = Range(start, max).diff(elements.toList).toList
    NonEmptyList.fromList(diff).toRight(()).swap
  }
}
