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
package properties

import cats.data.{Validated, ValidatedNel}
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._

import io.circe.Json

object CommonProperties {

  /**
   * AST representing value for `type` key in JSON Schema
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor79
   */
  sealed trait Type extends JsonSchemaProperty {
    def keyName = "type"
    def asJson: Json
    def withNull: Type = this match {
      case Type.Union(types) => Type.Union((Type.Null :: types).distinct)
      case Type.Null => Type.Null
      case other => Type.Union(List(Type.Null, other))
    }
  }

  object Type {
    case object Null extends Type {
      def asJson = Json.fromString("null")
    }

    case object Boolean extends Type {
      def asJson = Json.fromString("boolean")
    }

    case object String extends Type {
      def asJson = Json.fromString("string")
    }

    case object Integer extends Type {
      def asJson = Json.fromString("integer")
    }

    case object Number extends Type {
      def asJson = Json.fromString("number")
    }

    case object Array extends Type {
      def asJson = Json.fromString("array")
    }

    case object Object extends Type {
      def asJson = Json.fromString("object")
    }

    case class Union(value: List[Type]) extends Type {
      def asJson = Json.fromValues(value.map(_.asJson))

      def hasNull: Boolean = value.contains(Null)
    }


    private[jsonschema] def fromString(s: String): Either[String, Type] = s match {
      case "null"    => Right(Type.Null)
      case "boolean" => Right(Type.Boolean)
      case "string"  => Right(Type.String)
      case "integer" => Right(Type.Integer)
      case "number"  => Right(Type.Number)
      case "array"   => Right(Type.Array)
      case "object"  => Right(Type.Object)
      case other     => Left(other)
    }

    private[jsonschema] def fromProduct(arr: List[String]): Either[String, Type] =
      arr.map(fromString).map(_.toValidatedNel).sequence[ValidatedNel[String, ?], Type] match {
        case Validated.Valid(List(single)) => single.asRight
        case Validated.Valid(product) => Union(product).asRight
        case Validated.Invalid(invalid) if invalid.size == 1 => s"${invalid.head} is invalid type".asLeft
        case Validated.Invalid(invalid) => s"${invalid.toList.mkString(",")} are invalid types".asLeft
      }
  }

  /**
   * Type representing value for `enum` key
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor76
   */
  case class Enum(value: List[Json]) extends JsonSchemaProperty { def keyName = "enum" }


  /**
   * Type representing value for `oneOf` key
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor88
   */
  case class OneOf(value: List[Schema]) extends JsonSchemaProperty { def keyName = "oneOf" }


  /**
   * Type representing value for `description` key
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#rfc.section.10.1
   */
  case class Description(value: String) extends JsonSchemaProperty {
    def keyName = "description"
  }
}
