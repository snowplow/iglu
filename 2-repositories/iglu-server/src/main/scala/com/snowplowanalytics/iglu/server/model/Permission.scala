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
package com.snowplowanalytics.iglu.server
package model

import java.util.UUID

import cats.Order
import cats.implicits._
import cats.effect.Sync

import io.circe.Encoder
import io.circe.syntax._
import io.circe.generic.semiauto._

import doobie._
import doobie.postgres.implicits._

import storage.Storage.IncompatibleStorage

// Key A with x.y.z vendor CANNOT create key B with x.y vendor
// Key A with any non-empty KeyAction set always has read key-permissions

case class Permission(vendor: Permission.Vendor,
                      schema: Option[Permission.SchemaAction],
                      key: Set[Permission.KeyAction]) {
  /** Check if user has enough rights to read particular schema */
  def canRead(schemaVendor: String): Boolean =
    this match {
      case Permission(_, Some(_), _) =>
        vendor.check(schemaVendor)
      case Permission(_, _, keyActions) if keyActions.nonEmpty =>
        vendor.check(schemaVendor)
      case _ => false
    }

  /** Check if user has enough rights to create particular schema */
  def canCreateSchema(schemaVendor: String): Boolean =
    this match {
      case Permission(_, Some(action), _) if action != Permission.SchemaAction.Read =>
        vendor.check(schemaVendor)
      case Permission(_, _, keyActions) if keyActions.nonEmpty =>
        vendor.check(schemaVendor)
      case _ => false
    }

  /** Check if user has enough rights to create particular schema */
  def canCreatePermission(requestedVendor: String): Boolean =
    key.contains(Permission.KeyAction.Create) && vendor.check(requestedVendor)
}

object Permission {

  case class KeyPair(read: UUID, write: UUID)

  object KeyPair {
    def generate[F[_]](implicit F: Sync[F]): F[KeyPair] =
      F.delay(KeyPair(UUID.randomUUID(), UUID.randomUUID()))

    implicit val keyPairCirceEncoder: Encoder[KeyPair] =
      deriveEncoder[KeyPair]
  }

  implicit val permissionCirceEncoder: Encoder[Permission] =
    deriveEncoder[Permission]

  /**
    * Permission regarding vendor
    * @param parts dot-separated namespace, where permission can be applied
    * @param wildcard whether permission applied to any "smaller" vendor
    *                 or just specified in `parts`
    */
  case class Vendor(parts: List[String], wildcard: Boolean) {
    /** Check if this `vendor` from permission is allowed to work with `requestedVendor` */
    def check(requestedVendor: String): Boolean = {
      val requestedParts = requestedVendor.split("\\.").toList
      if (this == Vendor.wildcard) true
      else if (requestedParts == this.parts) true
      else if (requestedParts.take(this.parts.length) === this.parts && this.wildcard) true
      else false
    }

    /** Canonical vendor representation, e.g. "com.acme.iglu" */
    def asString: String = parts.mkString(".")

    def show: String = parts match {
      case Nil => "'wildcard vendor'"
      case _ => parts.mkString(".")
    }
  }

  object Vendor {
    /** Can be applied to any vendor */
    val wildcard = Vendor(Nil, true)

    /** Cannot be applied to any vendor */
    val noop = Vendor(Nil, false)

    def parse(string: String): Vendor =
      string match {
        case "*" => wildcard
        case _ if string.trim.isEmpty => wildcard
        case vendor => Vendor(vendor.split('.').toList, true)
      }

    implicit val vendorCirceEncoder: Encoder[Vendor] =
      Encoder.instance(_.asString.asJson)
  }

  sealed trait SchemaAction extends Product with Serializable {
    def show: String = this match {
      case Permission.SchemaAction.CreateVendor => "CREATE_VENDOR"
      case other => other.toString.toUpperCase
    }
  }
  object SchemaAction {
    /** Only get/view schemas */
    case object Read extends SchemaAction
    /** Bump schema versions within existing schema and read */
    case object Bump extends SchemaAction
    /** Create new schemas/names (but within attached vendor permission) */
    case object Create extends SchemaAction
    /** Do everything, including creating new "subvendor" (applied only for `Vendor` with `wildcard`) */
    case object CreateVendor extends SchemaAction

    val All: List[SchemaAction] =
      List(Read, Bump, Create, CreateVendor)

    implicit val ordering: Order[SchemaAction] =
      Order.by[SchemaAction, Int](All.zipWithIndex.toMap)

    def parse(string: String): Either[String, SchemaAction] =
      All.find(_.show === string).toRight(s"String $string is not valid SchemaAction")

    implicit val doobieSchemaActionGet: Meta[SchemaAction] =
      Meta[String]
        .timap(x => SchemaAction.parse(x).fold(e => throw IncompatibleStorage(e), identity))(_.show)

    implicit val schemaActionCirceEncoder: Encoder[SchemaAction] =
      Encoder.instance(_.show.asJson)
  }

  sealed trait KeyAction extends Product with Serializable {
    def show: String = this.toString.toUpperCase
  }
  object KeyAction {
    case object Create extends KeyAction
    case object Delete extends KeyAction

    val All: Set[KeyAction] = Set(Create, Delete)

    def parse(string: String): Either[String, KeyAction] =
      All.find(_.show === string).toRight(s"String $string is not valid KeyAction")

    implicit val doobieKeyActionGet: Meta[KeyAction] =
      Meta[String]
        .timap(x => parse(x).fold(e => throw IncompatibleStorage(e), identity))(_.show)

    implicit val keyActionCirceEncoder: Encoder[KeyAction] =
      Encoder.instance(_.show.asJson)
  }

  /** Anonymous user, authorized for nothing */
  val Noop = Permission(Vendor.noop, None, Set.empty)

  /** Admin permission, allowed to create any schemas and keys */
  val Master = Permission(Vendor.wildcard, Some(SchemaAction.CreateVendor), KeyAction.All)

  /** Read any schema */
  val ReadOnlyAny = Permission(Vendor.wildcard, Some(SchemaAction.Read), Set.empty)

  /** Read, write and create any schemas, but nothing for keys */
  val Write = Permission(Vendor.wildcard, Some(SchemaAction.CreateVendor), Set.empty)

  implicit val doobiePermissionRead: Read[Permission] =
    Read[(Option[String], Boolean, Option[SchemaAction], List[String])].map {
      case (ven, wildcard, schemaAction, keyAction) =>
        val vendor = ven.map(Vendor.parse).getOrElse(Vendor(Nil, wildcard))
        val keyActions = keyAction.traverse(KeyAction.parse).fold(e => throw IncompatibleStorage(e), identity)
        Permission(vendor, schemaAction, keyActions.toSet)
    }
}