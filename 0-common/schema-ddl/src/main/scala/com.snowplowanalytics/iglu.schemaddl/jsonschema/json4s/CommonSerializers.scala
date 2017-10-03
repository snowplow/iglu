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
import org.json4s.JsonAST.{JArray, JString}

// This library
import CommonProperties._

object CommonSerializers {

  import Json4sFromSchema._
  import Json4sToSchema._

  private def stringToType(json: JValue): Option[Type] = json match {
    case JString("null")    => Some(Null)
    case JString("boolean") => Some(Boolean)
    case JString("string")  => Some(String)
    case JString("integer") => Some(Integer)
    case JString("number")  => Some(Number)
    case JString("array")   => Some(Array)
    case JString("object")  => Some(Object)
    case _                  => None
  }

  object TypeSerializer extends CustomSerializer[Type](_ => (
    {
      case JArray(ts) =>
        val types = ts.map(stringToType)
        val union = if (types.exists(_.isEmpty)) None else Some(types.map(_.get))
        union match {
          case Some(List(t)) => t
          case Some(u)       => Product(u)
          case None          => throw new MappingException(ts + " is not valid list of types")
        }
      case str @ JString(t) =>
        stringToType(str) match {
          case Some(singleType) => singleType
          case None             => throw new MappingException(str + " is not valid list of types")
        }
      case x => throw new MappingException(x + " is not valid list of types")
    },

    {
      case t: Type => t.asJson
    }
    ))

  object DescriptionSerializer extends CustomSerializer[Description](x => (
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
      case JArray(values) => Enum(values)
      case x => throw new MappingException(x + " isn't valid enum")
    },

    {
      case Enum(values) => JArray(values)
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
