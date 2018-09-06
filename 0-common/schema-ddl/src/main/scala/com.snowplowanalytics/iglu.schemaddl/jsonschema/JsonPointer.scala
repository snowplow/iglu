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

import com.snowplowanalytics.iglu.schemaddl.jsonschema.JsonPointer._

case class JsonPointer private(value: List[Cursor]) extends AnyVal {
  def get: List[Cursor] = value.reverse

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

  sealed trait SchemaProperty { def key: Symbol }
  object SchemaProperty {
    case object Items extends SchemaProperty { def key = 'items }
    case object AdditionalItems extends SchemaProperty { def key = 'additionalItems }
    case object Properties extends SchemaProperty { def key = 'properties }
    case object AdditionalProperties extends SchemaProperty { def key = 'additionalProperties }
    case object PatternProperties extends SchemaProperty { def key = 'patternProperties }
    case object OneOf extends SchemaProperty { def key = 'oneOf }
  }

  sealed trait Cursor
  object Cursor {
    case class DownProperty(property: SchemaProperty) extends Cursor
    case class DownField(key: String) extends Cursor
    case class At(index: Int) extends Cursor
  }
}
