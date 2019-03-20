/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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
package jsonschema
package json4s

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{ parse, compact }

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._

// Circe
import io.circe.Json

// This library
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._

import implicits._

object CommonSerializers {

  // TODO: replace with AST-based
  private def fromCirce(json: Json): JValue =
    parse(json.noSpaces)

  private def toCirce(json: JValue): Json =
    json match {
      case JString(string) => Json.fromString(string)
      case JInt(int) => Json.fromBigInt(int)
      case JBool(bool) => Json.fromBoolean(bool)
      case JArray(arr) => Json.fromValues(arr.map(toCirce))
      case JDouble(num) => Json.fromDoubleOrNull(num)
      case JDecimal(decimal) => Json.fromBigDecimal(decimal)
      case JObject(fields) => Json.fromFields(fields.map { case (k, v) => (k, toCirce(v)) })
      case JNull => Json.Null
      case JNothing => Json.Null
    }

  object TypeSerializer extends CustomSerializer[Type](_ => (
    {
      case JArray(ts) =>
        val types = ts.map {
          case JString(s) => Type.fromString(s)
          case s => Left(compact(s))
        }
        types.sequence[Either[String, ?], Type] match {
          case Right(List(t)) => t
          case Right(u)       => Type.Union(u.toSet)
          case Left(invalid)  => throw new MappingException(invalid + " is not valid list of types")
        }
      case JString(t) =>
        Type.fromString(t) match {
          case Right(singleType) => singleType
          case Left(invalid)             => throw new MappingException(invalid + " is not valid list of types")
        }
      case x => throw new MappingException(x + " is not valid list of types")
    },

    {
      case t: Type => fromCirce(t.asJson)
    }
    ))

  object DescriptionSerializer extends CustomSerializer[Description](_ => (
    {
      case JString(value) => Description(value)
      case x => throw new MappingException(x + " isn't valid description")
    },

    {
      case Description(value) => JString(value)
    }
    ))


  object EnumSerializer extends CustomSerializer[Enum](_ => (
    {
      case JArray(values) => Enum(values.map(toCirce))
      case x => throw new MappingException(x + " isn't valid enum")
    },

    {
      case Enum(values) => JArray(values.map(fromCirce))
    }
    ))

  object OneOfSerializer extends CustomSerializer[OneOf](_ => (
    {
      case JArray(values) =>
        val schemas: List[Option[Schema]] = values.map(Schema.parse(_))
        if (schemas.forall(_.isDefined)) OneOf(schemas.map(_.get))
        else throw new MappingException(values + " need to be array of Schemas")
    },

    {
      case OneOf(schemas) => JArray(schemas.map(Schema.normalize(_)))
    }
    ))
}
