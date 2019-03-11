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

// Shadow Java Enum
import java.lang.{ Enum => _}

import cats.data.Validated

import matryoshka._
import matryoshka.implicits._

// This library
import properties._
import JsonPointer.SchemaProperty

/**
 * Class containing all (not yet) possible JSON Schema v4 properties
  */
case class Schema[A](multipleOf:           Option[NumberProperty.MultipleOf]        = None,
                     minimum:              Option[NumberProperty.Minimum]           = None,
                     maximum:              Option[NumberProperty.Maximum]           = None,

                     maxLength:            Option[StringProperty.MaxLength]         = None,
                     minLength:            Option[StringProperty.MinLength]         = None,
                     pattern:              Option[StringProperty.Pattern]           = None,
                     format:               Option[StringProperty.Format]            = None,

                     items:                Option[ArrayProperty.Items[A]]           = None,
                     additionalItems:      Option[ArrayProperty.AdditionalItems[A]] = None,
                     minItems:             Option[ArrayProperty.MinItems]           = None,
                     maxItems:             Option[ArrayProperty.MaxItems]           = None,

                     properties:           Option[ObjectProperty.Properties[A]]           = None,
                     additionalProperties: Option[ObjectProperty.AdditionalProperties[A]] = None,
                     required:             Option[ObjectProperty.Required]                = None,
                     patternProperties:    Option[ObjectProperty.PatternProperties[A]]    = None,

                     `type`:               Option[CommonProperties.Type]            = None,
                     enum:                 Option[CommonProperties.Enum]            = None,
                     oneOf:                Option[CommonProperties.OneOf[A]]        = None,
                     description:          Option[CommonProperties.Description]     = None) {

  private[iglu] val allProperties = List(multipleOf, minimum, maximum, maxLength, minLength,
    pattern, format, items, additionalItems, minItems, maxItems, properties,
    additionalProperties, required, patternProperties, `type`, enum, oneOf, description)
}

object Schema {
  /**
   * Parse arbitrary JSON AST as Schema class
   *
   * @param json JSON supposed to be JSON Schema
   * @tparam J JSON AST with [[ToSchema]] type class instance
   * @return some Schema if json is valid JSON Schema
   */
  def parse[J: ToSchema, A](json: J): Option[Schema[A]] =
    implicitly[ToSchema[J]].parse[A](json)

  /**
   * Transform correct JSON Schema into usual JSON AST
   *
   * @param schema [[Schema]] object
   * @tparam J JSON AST with [[FromSchema]] type class instance
   * @return JSON
   */
  def normalize[J: FromSchema, A](schema: Schema[A]): J =
    implicitly[FromSchema[J]].normalize(schema)

  implicit val schemaScalazFunctor: scalaz.Functor[Schema] = new scalaz.Functor[Schema] {
    override def map[A, B](fa: Schema[A])(f: A => B): Schema[B] = {
      val itemsO = fa.items.map {
        case ArrayProperty.Items.ListItems(a) =>
          ArrayProperty.Items.ListItems(f(a))
        case ArrayProperty.Items.TupleItems(list) =>
          ArrayProperty.Items.TupleItems(list.map(f))
      }
      val additionalItemsO = fa.additionalItems.map {
        case ArrayProperty.AdditionalItems.AdditionalItemsSchema(a) =>
          ArrayProperty.AdditionalItems.AdditionalItemsSchema(f(a))
        case ArrayProperty.AdditionalItems.AdditionalItemsAllowed(a) =>
          ArrayProperty.AdditionalItems.AdditionalItemsAllowed[B](a)
      }
      val propertiesO = fa.properties.map {
        case ObjectProperty.Properties(map) =>
          ObjectProperty.Properties(map.map { case (k, v) => (k, f(v)) })
      }
      val additionalPropertiesO = fa.additionalProperties.map {
        case ObjectProperty.AdditionalProperties.AdditionalPropertiesAllowed(b) =>
          ObjectProperty.AdditionalProperties.AdditionalPropertiesAllowed[B](b)
        case ObjectProperty.AdditionalProperties.AdditionalPropertiesSchema(a) =>
          ObjectProperty.AdditionalProperties.AdditionalPropertiesSchema(f(a))
      }
      val patternPropertiesO = fa.patternProperties.map {
        case ObjectProperty.PatternProperties(map) =>
          ObjectProperty.PatternProperties(map.map { case (k, v) => (k, f(v)) })
      }
      val oneOfO = fa.oneOf.map {
        case CommonProperties.OneOf(list) =>
          CommonProperties.OneOf(list.map(f))
      }
      Schema[B](fa.multipleOf, fa.minimum, fa.maximum, fa.maxLength, fa.minLength, fa.pattern, fa.format,
        itemsO, additionalItemsO, fa.minItems, fa.maxItems, propertiesO, additionalPropertiesO, fa.required,
        patternPropertiesO, fa.`type`, fa.enum, oneOfO, fa.description)
    }
  }

  val lint: Algebra[Schema, Option[Linter.Issue]] = { schema =>
    Linter.stringLength(???, schema).swap.toOption
  }

  type MyState[A] = cats.data.State[List[String], A]

  def addPath(path: String): MyState[Unit] =
    cats.data.State(s => (path :: s, Unit))

  val lintM: GAlgebraM[MyState, MyState, Schema, Option[Linter.Issue]] = { schema =>
    val q = schema.additionalItems.map(_.keyName)
    schema.additionalItems match {
      case Some(s) => s match {
        case ArrayProperty.AdditionalItems.AdditionalItemsSchema(mystate) =>
          for {
            _ <- addPath("additionItems")
            issue <- mystate
          } yield ()

      }
    }
    for {
      ss <- schema
    } yield ss

  }

  // F[A] => S[A]
}
