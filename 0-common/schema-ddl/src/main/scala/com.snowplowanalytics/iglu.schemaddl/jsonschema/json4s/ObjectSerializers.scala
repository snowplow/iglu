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
package jsonschema.json4s

// Scala
import scala.annotation.tailrec

// json4s
import org.json4s._

// This library
import jsonschema.Schema
import jsonschema.properties.ObjectProperty._


object ObjectSerializers {
  import ArraySerializers._
  import implicits._

  @tailrec private def allString(keys: List[JValue], acc: List[String] = Nil): Option[List[String]] = {
    keys match {
      case Nil             => Some(acc.reverse)
      case JString(h) :: t => allString(t, h :: acc)
      case _               => None
    }
  }

  object PropertiesSerializer extends CustomSerializer[Properties](_ => (
    {
      case obj: JObject =>
        obj.extractOpt[Map[String, JObject]].map { f =>
          f.map { case (key, v) => (key, Schema.parse(v: JValue).get)}
        } match {
          case Some(p) => Properties(p)
          case None => throw new MappingException("Isn't properties")
        }
      case x => throw new MappingException(x + " isn't properties")
    },

    {
      case Properties(fields) => JObject(fields.mapValues(Schema.normalize(_)).toList)
    }
    ))

  object AdditionalPropertiesSerializer extends CustomSerializer[AdditionalProperties](_ => (
    {
      case JBool(bool) => AdditionalProperties.AdditionalPropertiesAllowed(bool)
      case obj: JObject => Schema.parse(obj: JValue) match {
        case Some(schema) => AdditionalProperties.AdditionalPropertiesSchema(schema)
        case None => throw new MappingException(obj + " isn't additionalProperties")
      }
      case x => throw new MappingException(x + " isn't bool")
    },

    {
      case AdditionalProperties.AdditionalPropertiesAllowed(value) => JBool(value)
      case AdditionalProperties.AdditionalPropertiesSchema(value) => Schema.normalize(value)
    }
    ))

  object RequiredSerializer extends CustomSerializer[Required](_ => (
    {
      case JArray(keys) => allString(keys) match {
        case Some(k) => Required(k)
        case None => throw new MappingException("required array can contain only strings")
      }
      case x => throw new MappingException(x + " isn't bool")
    },

    {
      case Required(keys) => JArray(keys.map(JString))
    }
    ))

  object PatternPropertiesSerializer extends CustomSerializer[PatternProperties](_ => (
    {
      case obj: JObject =>
        obj.extractOpt[Map[String, JObject]].map { f =>
          f.map { case (key, v) => (key, Schema.parse(v: JValue).get)}
        } match {
          case Some(p) => PatternProperties(p)
          case None => throw new MappingException("Isn't patternProperties")
        }
      case x => throw new MappingException(x + " isn't patternProperties")
    },

    {
      case PatternProperties(fields) => JObject(fields.mapValues(Schema.normalize(_)).toList)
    }
    ))
}
