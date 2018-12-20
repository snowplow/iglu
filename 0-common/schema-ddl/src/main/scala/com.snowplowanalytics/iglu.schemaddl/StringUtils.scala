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

// IgluCore
import com.snowplowanalytics.iglu.core.SchemaMap

/**
 * Utilities for manipulating Strings
 */
object StringUtils {
  /**
   * Create a Redshift Table name from a schema
   *
   * "iglu:com.acme/PascalCase/jsonschema/13-0-0" -> "com_acme_pascal_case_13"
   *
   * @param schemaMap full Schema description
   * @return the Redshift Table name
   */
  def getTableName(schemaMap: SchemaMap): String = {
    // Split the vendor's reversed domain name using underscores rather than dots
    val snakeCaseOrganization = schemaMap.vendor.replaceAll( """\.""", "_").replaceAll("-", "_").toLowerCase

    // Change the name from PascalCase to snake_case if necessary
    val snakeCaseName = snakeCase(schemaMap.name)

    s"${snakeCaseOrganization}_${snakeCaseName}_${schemaMap.version.model}"
  }

  /**
   * Transforms CamelCase string into snake_case
   * Also replaces all hyphens with underscores
   *
   * @param str string to transform
   * @return the underscored string
   */
  def snakeCase(str: String): String =
    str.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
       .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
       .replaceAll("-", "_")
       .toLowerCase

  /**
   * Checks if comma-delimited string contains only integers (including negative)
   *
   * @param string string with items delimited by comma
   * @return true if string contains only integers
   */
  def isIntegerList(string: String): Boolean = {
    val elems = string.split(",").toList
    if (elems.isEmpty) { false }
    else {
      elems.forall { s =>
        s.headOption match {
          case Some('-') if s.length > 1 => s.tail.forall(_.isDigit)
          case _                         => s.forall(_.isDigit) }
      }
    }
  }

  /**
   * Utility object to match convertible strings
   */
  object IntegerAsString {
    def unapply(s : String) : Option[Int] = try {
      Some(s.toInt)
    } catch {
      case _: java.lang.NumberFormatException => None
    }
  }
}
