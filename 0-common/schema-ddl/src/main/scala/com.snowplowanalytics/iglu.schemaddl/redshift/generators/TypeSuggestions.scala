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
package com.snowplowanalytics.iglu.schemaddl.redshift
package generators

// Scalaz
import scalaz._
import Scalaz._

// This project
import com.snowplowanalytics.iglu.schemaddl.StringUtils._

/**
 * Module containing functions for data type suggestions
 */
object TypeSuggestions {
  /**
   * Type alias for function suggesting an encode type based on map of
   * JSON Schema properties
   */
  type DataTypeSuggestion = (Map[String, String], String) => Option[DataType]

  // For complex enums Suggest VARCHAR with length of longest element
  val complexEnumSuggestion: DataTypeSuggestion = (properties, columnName) =>
    properties.get("enum") match {
      case Some(enums) if isComplexEnum(enums) =>
        val longest = excludeNull(enums).map(_.length).max
        Some(RedshiftVarchar(longest))
      case _ => None
    }

  // Suggest VARCHAR(4096) for all product types. Should be in the beginning
  val productSuggestion: DataTypeSuggestion = (properties, columnName) =>
    properties.get("type") match {
      case (Some(types)) if excludeNull(types).size > 1 =>
        Some(ProductType(List(s"Product type $types encountered in $columnName")))
      case _ => None
    }

  val timestampSuggestion: DataTypeSuggestion = (properties, columnName) =>
    (properties.get("type"), properties.get("format")) match {
      case (Some(types), Some("date-time")) if types.contains("string") =>
        Some(RedshiftTimestamp)
      case _ => None
    }

  val dateSuggestion: DataTypeSuggestion = (properties, columnName) =>
    (properties.get("type"), properties.get("format")) match {
      case(Some(types), Some("date")) if types.contains("string") =>
        Some(RedshiftDate)
      case _ => None
    }

  val arraySuggestion: DataTypeSuggestion = (properties, columnName) =>
    properties.get("type") match {
      case Some(types) if types.contains("array") =>
        Some(RedshiftVarchar(5000))
      case _ => None
    }

  val numberSuggestion: DataTypeSuggestion = (properties, columnName) =>
    (properties.get("type"), properties.get("multipleOf")) match {
      case (Some(types), Some(multipleOf)) if types.contains("number") && multipleOf == "0.01" =>
        Some(RedshiftDecimal(Some(36), Some(2)))
      case (Some(types), _) if types.contains("number") =>
        Some(RedshiftDouble)
      case _ => None
    }

  val integerSuggestion: DataTypeSuggestion = (properties, columnName) => {
    (properties.get("type"), properties.get("maximum"), properties.get("enum"), properties.get("multipleOf")) match {
      case (Some(types), Some(maximum), _, _) if excludeNull(types) == Set("integer") =>
        getIntSize(maximum)
      // Contains only enum
      case (types, _, Some(enum), _) if (types.isEmpty || excludeNull(types.get) == Set("integer")) && isIntegerList(enum) =>
        val max = enum.split(",").toList.map(el => try Some(el.toLong) catch { case e: NumberFormatException => None } )
        val maxLong = max.sequence.getOrElse(Nil).maximum
        maxLong.flatMap(m => getIntSize(m))   // This will short-circuit integer suggestions on any non-integer enum
      case (Some(types), _, _, _) if excludeNull(types) == Set("integer") =>
        Some(RedshiftBigInt)
      case (Some(types), max, _, Some(multipleOf)) if types.contains("number") && multipleOf == "1" =>
        max.flatMap(m => getIntSize(m)).orElse(Some(RedshiftInteger))
      case _ => None
    }
  }

  val charSuggestion: DataTypeSuggestion = (properties, columnName) => {
    (properties.get("type"), properties.get("minLength"), properties.get("maxLength")) match {
      case (Some(types), Some(IntegerAsString(minLength)), Some(IntegerAsString(maxLength)))
        if minLength == maxLength && excludeNull(types) == Set("string") =>
        Some(RedshiftChar(maxLength))
      case _ => None
    }
  }

  val booleanSuggestion: DataTypeSuggestion = (properties, columnName) => {
    properties.get("type") match {
      case Some(types) if excludeNull(types) == Set("boolean") => Some(RedshiftBoolean)
      case _ => None
    }
  }

  val uuidSuggestion: DataTypeSuggestion = (properties, columnName) => {
    (properties.get("type"), properties.get("format")) match {
      case (Some(types), Some("uuid")) if types.contains("string") =>
        Some(RedshiftChar(36))
      case _ => None
    }
  }

  val varcharSuggestion: DataTypeSuggestion = (properties, columnName) => {
    (properties.get("type"), properties.get("maxLength"), properties.get("enum"), properties.get("format")) match {
      case (Some(types),     _,                           _,                      Some("ipv6")) if types.contains("string") =>
        Some(RedshiftVarchar(39))
      case (Some(types),     _,                           _,                      Some("ipv4")) if types.contains("string") =>
        Some(RedshiftVarchar(15))
      case (Some(types),     _,                           _,                      Some("email")) if types.contains("string") =>
        Some(RedshiftVarchar(255))
      case (Some(types),     Some(IntegerAsString(maxLength)), _,              _) if types.contains("string") =>
        Some(RedshiftVarchar(maxLength))
      case (_,              _,                            Some(enum),             _) => {
        val enumItems = enum.split(",")
        val maxLength = enumItems.toList.reduceLeft((a, b) => if (a.length > b.length) a else b).length
        if (enumItems.length == 1) {
          Some(RedshiftChar(maxLength))
        } else {
          Some(RedshiftVarchar(maxLength))
        }
      }
      case _ => None
    }
  }

  /**
   * Get set of types or enum as string excluding null
   *
   * @param types comma-separated types
   * @return set of strings
   */
  private def excludeNull(types: String): Set[String] = types.split(",").toSet - "null"

  /**
   * Helper function to get size of Integer
   *
   * @param max upper bound extracted from properties as string
   * @return Long representing biggest possible value or None if it's not Int
   */
  private def getIntSize(max: => String): Option[DataType] =
    try {
      val maxLong = max.toLong
      getIntSize(maxLong)
    } catch {
      case e: NumberFormatException => None
    }

  /**
   * Helper function to get size of Integer
   *
   * @param max upper bound
   * @return Long representing biggest possible value or None if it's not Int
   */
  private def getIntSize(max: Long): Option[DataType] =
    if (max <= Short.MaxValue) Some(RedshiftSmallInt)
    else if (max <= Int.MaxValue) Some(RedshiftInteger)
    else if (max <= Long.MaxValue) Some(RedshiftBigInt)
    else None

  /**
   * Check enum contains some different types
   * (string and number or number and boolean)
   */
  private def isComplexEnum(enum: String) = {
    // Predicates
    def isNumeric(s: String) = try {
      s.toDouble
      true
    } catch {
      case e: NumberFormatException => false
    }
    def isNonNumeric(s: String) = !isNumeric(s)
    def isBoolean(s: String) = s == "true" || s == "false"

    val nonNullEnum = excludeNull(enum)
    somePredicates(nonNullEnum, List(isNumeric _, isNonNumeric _, isBoolean _), 2)
  }

  /**
   * Check at least some `quantity` of `predicates` are true on `instances`
   *
   * @param instances list of instances to check on
   * @param predicates list of predicates to check
   * @param quantity required quantity
   */
  private def somePredicates(instances: Set[String], predicates: List[String => Boolean], quantity: Int): Boolean = {
    if (quantity == 0) true
    else predicates match {
      case Nil => false
      case h :: tail if instances.exists(h) => somePredicates(instances, tail, quantity - 1)
      case _ :: tail => somePredicates(instances, tail, quantity)
    }
  }
}
