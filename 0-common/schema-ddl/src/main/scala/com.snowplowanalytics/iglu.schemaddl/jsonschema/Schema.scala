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

import cats.Monad
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._

// This library
import properties._
import Pointer.SchemaProperty

/**
 * Class containing all (not yet) possible JSON Schema v4 properties
  */
case class Schema(multipleOf:           Option[NumberProperty.MultipleOf]           = None,
                  minimum:              Option[NumberProperty.Minimum]              = None,
                  maximum:              Option[NumberProperty.Maximum]              = None,

                  maxLength:            Option[StringProperty.MaxLength]            = None,
                  minLength:            Option[StringProperty.MinLength]            = None,
                  pattern:              Option[StringProperty.Pattern]              = None,
                  format:               Option[StringProperty.Format]               = None,

                  items:                Option[ArrayProperty.Items]                 = None,
                  additionalItems:      Option[ArrayProperty.AdditionalItems]       = None,
                  minItems:             Option[ArrayProperty.MinItems]              = None,
                  maxItems:             Option[ArrayProperty.MaxItems]              = None,

                  properties:           Option[ObjectProperty.Properties]           = None,
                  additionalProperties: Option[ObjectProperty.AdditionalProperties] = None,
                  required:             Option[ObjectProperty.Required]             = None,
                  patternProperties:    Option[ObjectProperty.PatternProperties]    = None,

                  `type`:               Option[CommonProperties.Type]               = None,
                  enum:                 Option[CommonProperties.Enum]               = None,
                  oneOf:                Option[CommonProperties.OneOf]              = None,
                  description:          Option[CommonProperties.Description]        = None) {

  private[iglu] val allProperties = List(multipleOf, minimum, maximum, maxLength, minLength,
    pattern, format, items, additionalItems, minItems, maxItems, properties,
    additionalProperties, required, patternProperties, `type`, enum, oneOf, description)
}

object Schema {

  /** Schema not containing any other child schemas */
  case class Primitive(schema: Schema) extends AnyVal

  /**
   * Parse arbitrary JSON AST as Schema class
   *
   * @param json JSON supposed to be JSON Schema
   * @tparam J JSON AST with [[ToSchema]] type class instance
   * @return some Schema if json is valid JSON Schema
   */
  def parse[J: ToSchema](json: J): Option[Schema] =
    implicitly[ToSchema[J]].parse(json)

  /**
   * Transform correct JSON Schema into usual JSON AST
   *
   * @param schema [[Schema]] object
   * @tparam J JSON AST with [[FromSchema]] type class instance
   * @return JSON
   */
  def normalize[J: FromSchema](schema: Schema): J =
    implicitly[FromSchema[J]].normalize(schema)

  def traverse[F[_], A](schema: Schema, f: (Pointer.SchemaPointer, Schema) => F[A])(implicit F: Monad[F]): F[A] = {
    def go(current: Schema, pointer: Pointer.SchemaPointer): F[A] =
      for {
        schema <- f(pointer, current)
        _ <- current.items match {
          case Some(ArrayProperty.Items.ListItems(value)) =>
            go(value, pointer.downProperty(SchemaProperty.Items))
          case Some(ArrayProperty.Items.TupleItems(values)) =>
            values
              .zipWithIndex
              .traverse { case (s, i) => go(s, pointer.downProperty(SchemaProperty.Items).at(i)) }
              .void
          case None => F.unit
        }
        _ <- current.additionalItems match {
          case Some(ArrayProperty.AdditionalItems.AdditionalItemsSchema(value)) =>
            go(value, pointer.downProperty(SchemaProperty.AdditionalItems))
          case _ => F.unit
        }
        _ <- current.properties match {
          case Some(ObjectProperty.Properties(value)) =>
            value
              .toList
              .traverse { case (k, s) => go(s, pointer.downProperty(SchemaProperty.Properties).downField(k)) }
              .void
          case _ => F.unit
        }
        _ <- current.additionalProperties match {
          case Some(ObjectProperty.AdditionalProperties.AdditionalPropertiesSchema(value)) =>
            go(value, pointer.downProperty(SchemaProperty.AdditionalProperties))
          case _ => F.unit
        }
        _ <- current.patternProperties match {
          case Some(ObjectProperty.PatternProperties(value)) =>
            value
              .toList
              .traverse { case (k, s) => go(s, pointer.downProperty(SchemaProperty.PatternProperties).downField(k)) }
              .void
          case _ => F.unit
        }
        _ <- current.oneOf match {
          case Some(CommonProperties.OneOf(values)) =>
            values
              .zipWithIndex
              .traverse { case (s, i) => go(s, pointer.downProperty(SchemaProperty.OneOf).at(i)) }
              .void
          case _ => F.unit
        }
      } yield schema

    go(schema, Pointer.Root)
  }

  val empty: Schema = Schema(None, None, None, None, None, None, None, None, None, None, None,
    None, None, None, None, None, None, None, None)
}
