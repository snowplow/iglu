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
import StringProperties._
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
     * Check if Schema has no specific type *OR* has no type at all
     */
    def withoutType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Product(types)) => !types.contains(jsonType)
        case Some(t) => t != jsonType
        case None => false            // absent type is ok
      }

    /**
     * Check if Schema has no specific type *OR* has no type at all
     */
    def withType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Product(types)) => types.contains(jsonType)
        case Some(t) => t == jsonType
        case None => false            // absent type is ok
      }

    /**
     * Check if Schema has specified format
     */
    def withFormat(format: Format): Boolean =
      value.format match {
        case Some(f) => format == f
        case None => false
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
  def lint(schema: Schema, severityLevel: SeverityLevel, height: Int): LintSchema = {
    // Current level validations
    val validations = severityLevel.linters.map(linter => linter(schema))
      .foldMap(_.toValidationNel)

    val rootTypeCheck =
      if(severityLevel == SecondLevel || severityLevel == ThirdLevel)
        (height match {
          case 0 =>
            (schema.`type`, schema.properties) match {
              case (Some(Object), None) => "Object Schema doesn't have properties".failure
              case (Some(Object), Some(Properties(_))) => propertySuccess
              case (_, _) => "Schema doesn't begin with type object".failure
            }
          case _ => propertySuccess
        }).toValidationNel
      else
        propertySuccess.toValidationNel


    // Validations of child nodes
    // In all following properties can be found child Schema

    val properties = schema.properties match {
      case Some(props) =>
        props.value.values.foldLeft(schemaSuccess)((a, s) => a |+| lint(s, severityLevel, height+1))
      case None => schemaSuccess
    }

    val patternProperties = schema.patternProperties match {
      case Some(PatternProperties(props)) =>
        props.values.foldLeft(schemaSuccess)((a, s) => a |+| lint(s, severityLevel, height+1))
      case _ => schemaSuccess
    }

    val additionalProperties = schema.additionalProperties match {
      case Some(AdditionalPropertiesSchema(s)) => lint(s, severityLevel, height+1)
      case _ => schemaSuccess
    }

    val items = schema.items match {
      case Some(ListItems(s)) => lint(s, severityLevel, height+1)
      case Some(TupleItems(i)) =>
        i.foldLeft(schemaSuccess)((a, s) => a |+| lint(s, severityLevel, height+1))
      case None => schemaSuccess
    }

    val additionalItems = schema.additionalItems match {
      case Some(AdditionalItemsSchema(s)) => lint(s, severityLevel, height+1)
      case _ => schemaSuccess
    }

    // summing current level validations, root type check and child nodes validations
    validations |+| rootTypeCheck |+| properties |+| items |+| additionalItems |+| additionalProperties |+| patternProperties
  }

  // Linter functions

  // First Severity Level

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
    * Check that string's `maxLength` property isn't greater than Redshift VARCHAR(max), 65535
    * See http://docs.aws.amazon.com/redshift/latest/dg/r_Character_types.html
    */
  val lintMaxLengthRange: Linter = (schema: Schema) => {
    if (schema.withType(String)) {
      schema.maxLength match {
        case Some(max) if max.value > 65535 => s"maxLength [${max.value}] is greater than Redshift VARCHAR(max), 65535".failure
        case _ => propertySuccess
      }
    }
    else propertySuccess
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
    * Check that schema contains known formats
    */
  val lintUnknownFormats: Linter = (schema: Schema) => {
    schema.format match {
      case Some(CustomFormat(format)) => s"Format [$format] is not supported. Available options are: date-time, date, email, hostname, ipv4, ipv6, uri".failure
      case _ => propertySuccess
    }
  }

  // Second Severity Level

  /**
   * Check that schema with type `number` or `integer` contains both minimum
   * and maximum properties
   */
  val lintMinMaxPresent: Linter = (schema: Schema) => {
    if (schema.withType(Number) || schema.withType(Integer)) {
      (schema.minimum, schema.maximum) match {
        case (Some(_), Some(_)) => propertySuccess
        case (None, Some(_)) => "Numeric Schema doesn't contain minimum property".failure
        case (Some(_), None) => "Numeric Schema doesn't contain maximum property".failure
        case _ => "Numeric Schema doesn't contain minimum and maximum properties".failure
      }
    }
    else propertySuccess
  }

  /**
   * Check that schema with type `string` contains `maxLength` property or has
   * other possibility to extract length
   */
  val lintMaxLength: Linter = (schema: Schema) => {
    if (schema.withType(String) && schema.enum.isEmpty && schema.maxLength.isEmpty) {
      if (schema.withFormat(Ipv4Format) || schema.withFormat(Ipv6Format) || schema.withFormat(DateTimeFormat)) {
        propertySuccess
      } else {
        "String Schema doesn't contain maxLength nor enum properties nor appropriate format".failure
      }
    } else {
      propertySuccess
    }
  }

  // Third Severity Level

  /**
    * Check that non-required properties have type null
    */
  val lintOptionalFields: Linter = (schema: Schema) => {
    (schema.required, schema.properties) match {
      case (Some(Required(required)), Some(Properties(properties))) =>
        val allowedKeys = properties.keySet
        val requiredKeys = required.toSet
        val optionalKeys = allowedKeys -- requiredKeys
        val optKeysWithoutTypeNull = for {
          key <- optionalKeys
          if !properties(key).withType(Null)
        } yield key
        optKeysWithoutTypeNull.isEmpty.or("It is recommended to express absence of property via nullable type")
      case _ => propertySuccess
    }
  }

  /**
   * Check that each 'field' contains a description property
   */
  val lintDescriptionPresent: Linter = (schema: Schema) => {
    schema.description match {
      case Some(_) => propertySuccess
      case None => schema.`type` match {
        case Some(t) => s"$t Schema doesn't contain description property".failure
        case None => s"Schema doesn't contain description property".failure
      }
    }
  }

  trait SeverityLevel {
    def linters: List[Linter]
  }

  case object FirstLevel extends SeverityLevel {
    val linters = List(
      // Check if some min cannot be greater than corresponding max
      lintMinimumMaximum, lintMinMaxLength, lintMinMaxItems,
      // Check if type of Schema corresponds with its validation properties
      lintNumberProperties, lintStringProperties, lintObjectProperties, lintArrayProperties,
      // Other checks
      lintPossibleKeys, lintUnknownFormats, lintMaxLengthRange
    )
  }

  case object SecondLevel extends SeverityLevel {
    val linters = FirstLevel.linters ++ List(lintMinMaxPresent, lintMaxLength)
  }

  case object ThirdLevel extends SeverityLevel {
    val linters = FirstLevel.linters ++ SecondLevel.linters ++ List(lintDescriptionPresent, lintOptionalFields)
  }
}
