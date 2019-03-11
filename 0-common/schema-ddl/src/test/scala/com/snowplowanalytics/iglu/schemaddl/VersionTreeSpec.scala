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

import com.snowplowanalytics.iglu.core.SchemaVer

import org.specs2.{ Specification, ScalaCheck }
import org.scalacheck.{ Arbitrary, Gen }

class VersionTreeSpec extends Specification with ScalaCheck { def is = s2"""
  add an addition after new revision $e1
  add two new additions $e2
  Models.add merges in next version $e3
  Revisions.add merges in next version $e4
  Additions.add merges in next version $e5
  Models.add merges versions in random order $e6
  VersionTree.build fails to build a tree with duplicate entry $e7
  VersionTree.build fails to build a tree with missing addition $e8
  VersionTree.build fails to build a tree with two missing additions $e9
  VersionTree.build fails to build a tree with missing revision $e10
  VersionTree.build builds an isomorphic VersionList $e11
  """

  """
  """.stripMargin

  def e1 = {
    val next = for {
      one <- VersionTree.Root.add(SchemaVer.Full(1,0,1))
      two <- one.add(SchemaVer.Full(1,1,0))
      four <- two.add(SchemaVer.Full(1,0,2))
    } yield four

    val expected = List(SchemaVer.Full(1,0,0), SchemaVer.Full(1,0,1), SchemaVer.Full(1,1,0), SchemaVer.Full(1,0,2))
    next.map(_.versionList.versions.toList) must beRight(expected)
  }

  def e2 = {
    val next = for {
      first <- VersionTree.Root.add(SchemaVer.Full(1,0,1))
      second <- first.add(SchemaVer.Full(1,0,2))
    } yield second
    val expected = List(SchemaVer.Full(1,0,0), SchemaVer.Full(1,0,1), SchemaVer.Full(1,0,2))

    next.map(_.versionList.versions.toList) must beRight(expected)
  }

  def e3 = {
    import VersionTree._
    import cats.data.NonEmptyList

    val init = VersionTree.Root

    // Two additions in the same revision group
    val expected = VersionTree(NonEmptyList.of(
      (1, Revisions(NonEmptyList.of(
        (0, Additions(NonEmptyList.of(1, 0)))) // 1, 0 likely
      )))
    )

    val result = init.add(SchemaVer.Full(1,0,1))
    result must beRight(expected)
  }

  def e4 = {
    import VersionTree._
    import cats.data.NonEmptyList

    val init = VersionTree.Root.models.head._2

    // Two additions in the same revision group
    val expected = Revisions(NonEmptyList.of(
      (0, Additions(NonEmptyList.of(1, 0))))
    )

    init.add(NonEmptyList.of(0), List(0), SchemaVer.Full(1,0,1)) must beRight(expected)
  }

  def e5 = {
    import VersionTree._
    import cats.data.NonEmptyList

    val init = Additions(NonEmptyList.of(0))
    val expected = Additions(NonEmptyList.of(1, 0))

    init.add(List(0), 1) must beRight(expected)
  }

  def e6 = {
    val next = for {
      one <- VersionTree.Root.add(SchemaVer.Full(1,0,1))
      two <- one.add(SchemaVer.Full(1,1,0))
      seven <- two.add(SchemaVer.Full(2,0,0))
      thr <- seven.add(SchemaVer.Full(2,1,0))
      foru <- thr.add(SchemaVer.Full(2,2,0))
    } yield foru

    val expected = List(SchemaVer.Full(1,0,0), SchemaVer.Full(1,0,1), SchemaVer.Full(1,1,0),
      SchemaVer.Full(2,0,0), SchemaVer.Full(2,1,0), SchemaVer.Full(2,2,0))
    next.map(_.versionList.versions.toList) must beRight(expected)
  }


  def e7 = {
    val original = List(SchemaVer.Full(1,0,0),
      SchemaVer.Full(1,0,1), SchemaVer.Full(1,1,0),
      SchemaVer.Full(2,0,0), SchemaVer.Full(2,1,0), SchemaVer.Full(2,2,0),
      SchemaVer.Full(2,1,0)
    )

    VersionTree.build(original).map(_.versionList.versions.toList) must beLeft.like {
      case VersionTree.BuildingError.InvalidTree(VersionTree.AddingError.AlreadyExists, _, SchemaVer.Full(2,1,0)) => ok
      case _ => ko
    }
  }

  def e8 = {
    import VersionTree._

    val original = List(SchemaVer.Full(1,0,0), SchemaVer.Full(2,0,1))

    VersionTree.build(original).map(_.versionList.versions.toList) must beLeft.like {
      case BuildingError.InvalidTree(AddingError.AdditionGaps(NonEmptyList(0, Nil)), _, SchemaVer.Full(2,0,1)) => ok
      case _ => ko
    }
  }

  def e9 = {
    import VersionTree._

    val original = List(SchemaVer.Full(1,0,0), SchemaVer.Full(1,0,3))

    VersionTree.build(original).map(_.versionList.versions.toList) must beLeft.like {
      case BuildingError.InvalidTree(AddingError.AdditionGaps(NonEmptyList(1, List(2))), _, SchemaVer.Full(1,0,3)) => ok
      case _ => ko
    }
  }

  def e10 = {
    import VersionTree._

    val original = List(SchemaVer.Full(1,0,0), SchemaVer.Full(1,2,0))

    VersionTree.build(original).map(_.versionList.versions.toList) must beLeft.like {
      case BuildingError.InvalidTree(AddingError.RevisionGaps(NonEmptyList(1, Nil)), _, SchemaVer.Full(1,2,0)) => ok
      case _ => ko
    }
  }

  def e11 = {
    import VersionTreeSpec._

    prop { versions: NonEmptyList[SchemaVer.Full] =>
      val result = VersionTree.build(versions.toList)
      result must beRight.like {
        case tree => tree.versionList.versions must beEqualTo(versions)
      }
    }
  }

}

object VersionTreeSpec {
  sealed trait SchemaVerPoint extends Product with Serializable
  object SchemaVerPoint {
    case object Model extends SchemaVerPoint
    case object Revision extends SchemaVerPoint
    case object Addition extends SchemaVerPoint

    val gen = Gen.frequency((1, Model), (2, Revision), (10, Addition))
  }

  def shuffled(start: SchemaVer.Full): Gen[NonEmptyList[SchemaVer.Full]] = {
    def go(acc: List[SchemaVer.Full]): Gen[NonEmptyList[SchemaVer.Full]] =
      for {
        stop <- Gen.frequency((10, false), (1, true))
        previous <- Gen.oneOf(acc)
        nextPoint <- SchemaVerPoint.gen
        next = nextPoint match {
          case SchemaVerPoint.Model =>
            previous.copy(model = previous.model + 1, revision = 0, addition = 0)
          case SchemaVerPoint.Revision =>
            previous.copy(revision = previous.revision + 1, addition = 0)
          case SchemaVerPoint.Addition =>
            previous.copy(addition = previous.addition + 1)
        }
        updated = if (acc.contains(next)) acc else next :: acc
        result <- if (stop) Gen.const(NonEmptyList.fromListUnsafe(updated)) else go(updated)
      } yield result

    go(List(start))
  }


  def sequential(start: SchemaVer.Full): Gen[NonEmptyList[SchemaVer.Full]] = {
    def go(acc: List[SchemaVer.Full], previous: SchemaVer.Full): Gen[NonEmptyList[SchemaVer.Full]] =
      for {
        stop <- Gen.frequency((10, false), (1, true))
        nextPoint <- SchemaVerPoint.gen
        next = nextPoint match {
          case SchemaVerPoint.Model =>
            previous.copy(model = previous.model + 1, revision = 0, addition = 0)
          case SchemaVerPoint.Revision =>
            previous.copy(revision = previous.revision + 1, addition = 0)
          case SchemaVerPoint.Addition =>
            previous.copy(addition = previous.addition + 1)
        }
        result <- if (stop) Gen.const(NonEmptyList(previous, acc)) else go(previous :: acc, next)
      } yield result

    go(Nil, start)
  }

  implicit val versionListArb: Arbitrary[NonEmptyList[SchemaVer.Full]] =
    Arbitrary(VersionTreeSpec.shuffled(SchemaVer.Full(1,0,0)).map(_.reverse))
}
