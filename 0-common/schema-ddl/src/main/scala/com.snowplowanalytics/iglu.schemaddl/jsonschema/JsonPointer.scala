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

import com.snowplowanalytics.iglu.schemaddl.jsonschema.JsonPointer._

case class JsonPointer private(value: List[Cursor]) extends AnyVal {
  def get: List[Cursor] = value.reverse

  def last = value.headOption

  def show: String = "/" ++ (get.map {
    case Cursor.DownField(key) => key
    case Cursor.DownProperty(property) => property.key.name
    case Cursor.At(index) => index.toString
  } mkString "/")

  def downProperty(schemaProperty: SchemaProperty): JsonPointer =
    JsonPointer(Cursor.DownProperty(schemaProperty) :: value)
  def downField(key: String): JsonPointer =
    JsonPointer(Cursor.DownField(key) :: value)
  def at(index: Int): JsonPointer =
    JsonPointer(Cursor.At(index) :: value)
}

object JsonPointer {

  val Root = JsonPointer(Nil)

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
  def parse(string: String): Either[JsonPointer, JsonPointer] = {
    @tailrec def go(remaining: List[String], acc: JsonPointer): Either[JsonPointer, JsonPointer] = {
      remaining match {
        case Nil => acc.asRight
        case current :: tail =>
          def giveUp =
            JsonPointer((current :: tail).reverse.map(Cursor.DownField.apply) ++ acc.value).asLeft
          acc match {
            case Root => SchemaProperty.fromString(current) match {
              case Right(property) => go(tail, Root.downProperty(property))
              case Left(_) => giveUp
            }
            case JsonPointer(previousCursor :: old) => previousCursor match {
              case Cursor.DownProperty(property) =>
                property.next(current) match {
                  case Right(next) => go(tail, JsonPointer(next :: previousCursor :: old))
                  case Left(_) => giveUp
                }
              case _ => SchemaProperty.fromString(current) match {
                case Right(next) => go(tail, JsonPointer(Cursor.DownProperty(next) :: previousCursor :: old))
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
    case class DownProperty(property: SchemaProperty) extends Cursor
    case class DownField(key: String) extends Cursor
    case class At(index: Int) extends Cursor
  }
}
