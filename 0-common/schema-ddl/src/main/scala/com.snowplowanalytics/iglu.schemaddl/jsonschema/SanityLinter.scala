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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

// Scalaz
import scalaz._
import Scalaz._

// This library
import CommonProperties._
import ArrayProperties.{AdditionalItemsSchema, ListItems, TupleItems}
import ObjectProperties.{ AdditionalPropertiesSchema, AdditionalPropertiesAllowed, PatternProperties, Properties, Required }


/**
 * Contains Schema validation logic for JSON AST to find nonsense (impossible)
 * JSON Schemas, ie. Schemas which cannot validate ANY value, yet
 * syntactically correct.
 * This doesn't have logic to validate accordance to JSON Schema specs such as
 * non-empty `required` or numeric `maximum`. Separate validator should be
 * used for that.
 *
 * @see https://github.com/snowplow/iglu/issues/164
 */
object SanityLinter {

  /**
   * Aggregated property lints
   */
  type LintSchema = ValidationNel[String, Unit]

  /**
   * Check of single property
   */
  type LintProperty = Validation[String, Unit]

  /**
   * Function able to lint Schema AST
   */
  type Linter = (Schema) => LintProperty

  /**
   * Whole subschema was processed successfully
   */
  val schemaSuccess = ().successNel[String]

  /**
   * Some property was processed successfully
   */
  val propertySuccess = ().success[String]

  /**
   * Pimp boolean, so it can pipe failure in case of `false`
   */
  private implicit class LintOps(val value: Boolean) {
    /**
     * Return failure message if condition is false
     */
    def or(message: String): LintProperty =
      if (value) propertySuccess else message.failure
  }

  /**
   * Pimp JSON Schema AST with method checking presence of some JSON type
   */
  private implicit class SchemaOps(val value: Schema) {
    /**
     * Check if Schema has no specifict type *OR* has no type at all
     */
    def withoutType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Product(types)) => !types.contains(jsonType)
        case Some(t) => t != jsonType
        case None => false            // absent type is ok
      }
  }

  /**
   * Main working function, traversing JSON Schema
   * It lints all properties on current level, then tries to extract all
   * subschemas from properties like `items`, `additionalItems` etc and
   * recursively lint them as well
   *
   * @param schema parsed JSON AST
   * @return non-empty list of summed failures (all, including nested) or
   *         unit in case of success
   */
  def lint(schema: Schema): LintSchema = {
    // Current level validations
    val validations = linters.map(linter => linter(schema))
      .foldMap(_.toValidationNel)

    // Validations of child nodes
    // In all following properties can be found child Schema

    val properties = schema.properties match {
      case Some(props) =>
        props.value.values.foldLeft(schemaSuccess)((_, s) => lint(s))
      case None => schemaSuccess
    }

    val patternProperties = schema.patternProperties match {
      case Some(PatternProperties(props)) =>
        props.values.foldLeft(schemaSuccess)((_, s) => lint(s))
      case _ => schemaSuccess
    }

    val additionalProperties = schema.additionalProperties match {
      case Some(AdditionalPropertiesSchema(s)) => lint(s)
      case _ => schemaSuccess
    }

    val items = schema.items match {
      case Some(ListItems(s)) => lint(s)
      case Some(TupleItems(i)) =>
        i.foldLeft(schemaSuccess)((_, s) => lint(s))
      case None => schemaSuccess
    }

    val additionalItems = schema.additionalItems match {
      case Some(AdditionalItemsSchema(s)) => lint(s)
      case _ => schemaSuccess
    }

    // summing current level validations and child nodes validations
    validations |+| properties |+| items |+| additionalItems |+| additionalProperties |+| patternProperties
  }

  // Linter functions

  /**
   * Check that number's `minimum` property isn't greater than `maximum`
   */
  val lintMinimumMaximum: Linter = (schema: Schema) => {
    (schema.minimum, schema.maximum) match {
      case (Some(min), Some(max)) =>
        (max.getAsDecimal >= min.getAsDecimal)
          .or(s"minimum property [${min.getAsDecimal}] is greater than maximum [${max.getAsDecimal}]")
      case _ => propertySuccess
    }
  }

  /**
   * Check that string's `minLength` property isn't greater than `maxLength`
   */
  val lintMinMaxLength: Linter = (schema: Schema) => {
    (schema.minLength, schema.maxLength) match {
      case (Some(min), Some(max)) =>
        (max.value >= min.value).or(s"minLength property [${min.value}] is greater than maxLength [${max.value}]")
      case _ => propertySuccess
    }
  }

  /**
   * Check that array's `minItems` property isn't greater than `maxItems`
   */
  val lintMinMaxItems: Linter = (schema: Schema) => {
    (schema.minItems, schema.maxItems) match {
      case (Some(min), Some(max)) =>
        (max.value >= min.value).or(s"minItems property [${min.value}] is greater than maxItems [${max.value}]")
      case _ => propertySuccess
    }
  }

  /**
   * Check that Schema with non-numeric type doesn't contain numeric properties
   */
  val lintNumberProperties: Linter = (schema: Schema) => {
    val numberProperties = schema.allProperties.collect {
      case Some(p: NumberProperties.NumberProperty) => p
    }
    val fruitless = numberProperties.nonEmpty && (schema.withoutType(Number) && schema.withoutType(Integer))
    (!fruitless).or(s"Properties [${numberProperties.map(_.keyName).mkString(",")}] require number, integer or absent type")
  }

  /**
   * Check that Schema with non-string type doesn't contain string properties
   */
  val lintStringProperties: Linter = (schema: Schema) => {
    val stringProperties = schema.allProperties.collect {
      case Some(p: StringProperties.StringProperty) => p
    }
    val fruitless = stringProperties.nonEmpty && schema.withoutType(String)
    (!fruitless).or(s"Properties [${stringProperties.map(_.keyName).mkString(",")}] require string or absent type")
  }

  /**
   * Check that Schema with non-object type doesn't contain object properties
   */
  val lintObjectProperties: Linter = (schema: Schema) => {
    val objectProperties = schema.allProperties.collect {
      case Some(p: ObjectProperties.ObjectProperty) => p
    }
    val fruitless = objectProperties.map(_.keyName).nonEmpty && schema.withoutType(Object)
    (!fruitless).or(s"Properties [${objectProperties.map(_.keyName).mkString(",")}] require object or absent type")
  }

  /**
   * Check that Schema with non-object type doesn't contain object properties
   */
  val lintArrayProperties: Linter = (schema: Schema) => {
    val arrayProperties = schema.allProperties.collect {
      case Some(p: ArrayProperties.ArrayProperty) => p
    }
    val fruitless = arrayProperties.nonEmpty && schema.withoutType(Array)
    (!fruitless).or(s"Properties [${arrayProperties.map(_.keyName).mkString(",")}] require array or absent type")
  }

  /**
   * Check that all required keys listed in properties
   * @todo take `patternProperties` in account
   */
  val lintPossibleKeys: Linter = (schema: Schema) => {
    (schema.additionalProperties, schema.required, schema.properties, schema.patternProperties) match {
      case (Some(AdditionalPropertiesAllowed(false)), Some(Required(required)), Some(Properties(properties)), None) =>
        val allowedKeys = properties.keySet
        val requiredKeys = required.toSet
        val diff = requiredKeys -- allowedKeys
        diff.isEmpty.or(s"Properties [${diff.mkString(",")}] is required, but not listed in properties")
      case _ => propertySuccess
    }
  }

  /**
   * List of all available linters
   */
  val linters = List(
    // Check if some min cannot be greater than corresponding max
    lintMinimumMaximum, lintMinMaxLength, lintMinMaxItems,
    // Check if type of Schema corresponds with its validation properties
    lintNumberProperties, lintStringProperties, lintObjectProperties, lintArrayProperties,
    // Other checks
    lintPossibleKeys
  )
}

