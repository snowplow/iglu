package com.snowplowanalytics.iglu.schemaddl.jsonschema

// Shadow Java Enum
import java.lang.{ Enum => _}

// This library
import properties._

import Delta._
case class Delta(multipleOf:           Changed[NumberProperty.MultipleOf]           = unchanged,
                 minimum:              Changed[NumberProperty.Minimum]              = unchanged,
                 maximum:              Changed[NumberProperty.Maximum]              = unchanged,

                 maxLength:            Changed[StringProperty.MaxLength]            = unchanged,
                 minLength:            Changed[StringProperty.MinLength]            = unchanged,
                 pattern:              Changed[StringProperty.Pattern]              = unchanged,
                 format:               Changed[StringProperty.Format]               = unchanged,

                 items:                Changed[ArrayProperty.Items]                 = unchanged,
                 additionalItems:      Changed[ArrayProperty.AdditionalItems]       = unchanged,
                 minItems:             Changed[ArrayProperty.MinItems]              = unchanged,
                 maxItems:             Changed[ArrayProperty.MaxItems]              = unchanged,

                 properties:           Changed[ObjectProperty.Properties]           = unchanged,
                 additionalProperties: Changed[ObjectProperty.AdditionalProperties] = unchanged,
                 required:             Changed[ObjectProperty.Required]             = unchanged,
                 patternProperties:    Changed[ObjectProperty.PatternProperties]    = unchanged,

                 `type`:               Changed[CommonProperties.Type]               = unchanged,
                 enum:                 Changed[CommonProperties.Enum]               = unchanged,
                 oneOf:                Changed[CommonProperties.OneOf]              = unchanged,
                 description:          Changed[CommonProperties.Description]        = unchanged) {

  private[iglu] val allProperties: List[Changed[JsonSchemaProperty]] =
    List(multipleOf, minimum, maximum, maxLength, minLength,
      pattern, format, items, additionalItems, minItems, maxItems, properties,
      additionalProperties, required, patternProperties, `type`, enum, oneOf, description)

  def getChanged: List[Changed[JsonSchemaProperty]] =
    allProperties.filter(_.nonEmpty)

}

object Delta {
  case class Changed[+A](was: Option[A], became: Option[A]) {
    def nonEmpty: Boolean = was.isDefined || became.isDefined
  }

  def build(original: Schema, target: Schema): Delta =
    Delta(
      check(original.multipleOf,           target.multipleOf),
      check(original.minimum,              target.minimum),
      check(original.maximum,              target.maximum),

      check(original.maxLength,            target.maxLength),
      check(original.minLength,            target.minLength),
      check(original.pattern,              target.pattern),
      check(original.format,               target.format),

      check(original.items,                target.items),
      check(original.additionalItems,      target.additionalItems),
      check(original.minItems,             target.minItems),
      check(original.maxItems,             target.maxItems),

      check(original.properties,           target.properties),
      check(original.additionalProperties, target.additionalProperties),
      check(original.required,             target.required),
      check(original.patternProperties,    target.patternProperties),

      check(original.`type`,               target.`type`),
      check(original.enum,                 target.enum),
      check(original.oneOf,                target.oneOf),
      check(original.description,          target.description))

  def check[A <: JsonSchemaProperty](one: Option[A], two: Option[A]): Changed[A] =
    if (one == two) unchanged[A] else Changed(one, two)

  def unchanged[A <: JsonSchemaProperty]: Changed[A] =
    Changed[A](None, None)
}
