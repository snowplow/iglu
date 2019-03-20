/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

import cats.syntax.either._

import scala.annotation.tailrec

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Pointer._

sealed trait Pointer extends Product with Serializable {
  def value: List[Cursor]

  def get: List[Cursor] = value.reverse

  def last: Option[Cursor] = value.headOption

  def parent: Option[SchemaPointer] = value match {
    case Nil => None
    case _ :: t => Some(SchemaPointer(t))
  }

  def show: String = "/" ++ (get.map {
    case Cursor.DownField(key) => key
    case Cursor.DownProperty(property) => property.key.name
    case Cursor.At(index) => index.toString
  } mkString "/")

  def downField(key: String): SchemaPointer =
    SchemaPointer(Cursor.DownField(key) :: value)
  def at(index: Int): SchemaPointer =
    SchemaPointer(Cursor.At(index) :: value)

  def isParentOf(child: Pointer): Boolean =
    child.get.startsWith(this.get)
}

object Pointer {

  val Root = SchemaPointer(Nil)

  /** JSON Pointer that cannot have `CursorProperty` */
  final case class JsonPointer private(value: List[Cursor]) extends Pointer {
    def path: List[String] = get.flatMap {
      case Cursor.DownField(field) => List(field)
      case _ => None
    }
  }

  // TODO: we should refactor Pointer to make it Schema-agnostic, instead SchemaPointer should be a newtype

  /** Special case of JSON Pointer, working with JSON Schemas instead of generic JSON */
  final case class SchemaPointer private(value: List[Cursor]) extends Pointer {
    def downProperty(schemaProperty: SchemaProperty): SchemaPointer =
      SchemaPointer(Cursor.DownProperty(schemaProperty) :: value)

    def forData: JsonPointer =
      JsonPointer(value.flatMap {
        case cur: Pointer.Cursor.DownField => List(cur)
        case _ => Nil
      })
  }


  sealed trait SchemaProperty extends Product with Serializable {
    import SchemaProperty._

    def key: Symbol

    /** Get a parse function that will enforce correct Cursor
      * (e.g. only indexes allowed in Items and OneOf as they're arrays)
      */
    def next: String => Either[String, Cursor] = this match {
      case Items => i => Either.catchNonFatal(i.toInt).leftMap(_.getMessage).map(Cursor.At.apply)
      case OneOf => i => Either.catchNonFatal(i.toInt).leftMap(_.getMessage).map(Cursor.At.apply)
      case Properties        => i => Cursor.DownField(i).asRight
      case PatternProperties => i => Cursor.DownField(i).asRight
      case AdditionalItems      => i => fromString(i).map(Cursor.DownProperty.apply)
      case AdditionalProperties => i => fromString(i).map(Cursor.DownProperty.apply)
    }
  }

  object SchemaProperty {
    case object Items extends SchemaProperty { def key = 'items }
    case object AdditionalItems extends SchemaProperty { def key = 'additionalItems }
    case object Properties extends SchemaProperty { def key = 'properties }
    case object AdditionalProperties extends SchemaProperty { def key = 'additionalProperties }
    case object PatternProperties extends SchemaProperty { def key = 'patternProperties }
    case object OneOf extends SchemaProperty { def key = 'oneOf }

    val all = List(Items, AdditionalItems, Properties, AdditionalProperties, PatternProperties, OneOf)

    def fromString(s: String): Either[String, SchemaProperty] =
      all.find(x => x.key.name == s).toRight(s)
  }

  /**
    * Parse function, that tries to preserve correct cursors
    * In case structure of fields is incorrect it fallbacks to Left all-DownField,
    * which gives same string representation, but can be wrong semantically
    */
  def parseSchemaPointer(string: String): Either[SchemaPointer, SchemaPointer] = {
    @tailrec def go(remaining: List[String], acc: SchemaPointer): Either[SchemaPointer, SchemaPointer] = {
      remaining match {
        case Nil => acc.asRight
        case current :: tail =>
          def giveUp =
            SchemaPointer((current :: tail).reverse.map(Cursor.DownField.apply) ++ acc.value).asLeft
          acc match {
            case Root => SchemaProperty.fromString(current) match {
              case Right(property) => go(tail, Root.downProperty(property))
              case Left(_) => giveUp
            }
            case SchemaPointer(previousCursor :: old) => previousCursor match {
              case Cursor.DownProperty(property) =>
                property.next(current) match {
                  case Right(next) => go(tail, SchemaPointer(next :: previousCursor :: old))
                  case Left(_) => giveUp
                }
              case _ => SchemaProperty.fromString(current) match {
                case Right(next) => go(tail, SchemaPointer(Cursor.DownProperty(next) :: previousCursor :: old))
                case Left(_) => giveUp
              }
            }
          }
      }
    }

    go(string.split("/").filter(_.nonEmpty).toList, Root)
  }

  sealed trait Cursor
  object Cursor {
    case class DownField(key: String) extends Cursor
    case class At(index: Int) extends Cursor
    /** Special keys of `DownField` working only with `SchemaPointer` */
    case class DownProperty(property: SchemaProperty) extends Cursor
  }
}
