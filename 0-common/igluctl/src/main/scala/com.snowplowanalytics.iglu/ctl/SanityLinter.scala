/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.ctl

// scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s.JValue

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.CommonProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.ArrayProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.ObjectProperties._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.Json4sToSchema._


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
   * Primary function, accepts any JSON, returns information about its sanity
   *
   * @param json JSON supposed to be JSON Schema
   * @return aggregated list of errors or Unit
   */
  def lint(json: JValue): LintSchema = {
    Schema.parse(json) match {
      case Some(schema) => checkSchema(schema)
      case None => "JSON value doesn't contain JSON Schema".failureNel[Unit]
    }
  }

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
    def or(message: String): LintProperty =
      if (value) propertySuccess else message.failure
  }

  /**
   * Pimp JSON Schema AST with method checking presence of some JSON type
   */
  private implicit class SchemaOps(val value: Schema) {
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
  def checkSchema(schema: Schema): LintSchema = {
    // Current level validations
    val validations = linters.map(linter => linter(schema))
                             .foldMap(_.toValidationNel)

    // Validations of child nodes
    // In all following properties can be found child Schema

    val properties = schema.properties match {
      case Some(props) =>
        props.value.values.foldLeft(schemaSuccess)((_, s) => checkSchema(s))
      case None => schemaSuccess
    }

    val patternProperties = schema.patternProperties match {
      case Some(PatternProperties(props)) =>
        props.values.foldLeft(schemaSuccess)((_, s) => checkSchema(s))
      case _ => schemaSuccess
    }

    val additionalProperties = schema.additionalProperties match {
      case Some(AdditionalPropertiesSchema(s)) => checkSchema(s)
      case _ => schemaSuccess
    }

    val items = schema.items match {
      case Some(ListItems(s)) => checkSchema(s)
      case Some(TupleItems(i)) =>
        i.foldLeft(schemaSuccess)((_, s) => checkSchema(s))
      case None => schemaSuccess
    }

    val additionalItems = schema.additionalItems match {
      case Some(AdditionalItemsSchema(s)) => checkSchema(s)
      case _ => schemaSuccess
    }

    // summing current level validations and child nodes validations
    validations |+| properties |+| items |+| additionalItems |+| additionalProperties |+| patternProperties
  }

  // Lint functions

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
    val numberProperties = List(schema.minimum, schema.maximum, schema.multipleOf).flatten
    val size = numberProperties.size
    val fruitless = size > 0 && (schema.withoutType(Number) && schema.withoutType(Integer))
    (!fruitless).or(s"Properties [${numberProperties.mkString(",")}] require number, integer or absent type")
  }

  /**
   * Check that Schema with non-string type doesn't contain string properties
   */
  val lintStringProperties: Linter = (schema: Schema) => {
    val stringProperties = List(schema.maxLength, schema.minLength, schema.pattern, schema.format).flatten
    val size = stringProperties.size
    val fruitless = size > 0 && schema.withoutType(String)
    (!fruitless).or(s"Properties [${stringProperties.mkString(",")}] require string or absent type")
  }

  /**
   * Check that Schema with non-object type doesn't contain object properties
   */
  val lintObjectProperties: Linter = (schema: Schema) => {
    val objectProperties =
      List(schema.properties, schema.patternProperties, schema.required, schema.additionalProperties).flatten
    val size = objectProperties.size
    val fruitless = size > 0 && schema.withoutType(Object)
    (!fruitless).or(s"Properties [${objectProperties.mkString(",")}] require object or absent type")
  }

  /**
   * Check that Schema with non-object type doesn't contain object properties
   */
  val lintArrayProperties: Linter = (schema: Schema) => {
    val arrayProperties = List(schema.items, schema.additionalItems, schema.minItems, schema.maxItems).flatten
    val size = arrayProperties.size
    val fruitless = size > 0 && schema.withoutType(Array)
    (!fruitless).or(s"Properties [${arrayProperties.mkString(",")}] require array or absent type")
  }

  /**
   * List of all available linters
   */
  val linters = List(
    // Check if some min cannot be greater than max
    lintMinimumMaximum, lintMinMaxLength, lintMinMaxItems,
    // Check if type of Schema corresponds with its validation properties
    lintNumberProperties, lintStringProperties, lintObjectProperties, lintArrayProperties
  )
}
