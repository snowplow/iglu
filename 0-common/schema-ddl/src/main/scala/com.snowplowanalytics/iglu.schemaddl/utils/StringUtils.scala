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
package utils

// This library
import SchemaData.SelfDescInfo

/**
 * Utilities for manipulating Strings
 */
object StringUtils {
  /**
   * Will return the longest string in an Iterable
   * string list.
   *
   * @param list The list of strings to analyze
   * @return the longest string in the list
   */
  def getLongest(list: Iterable[String]): String =
    list.reduceLeft((x,y) => if (x.length > y.length) x else y)

  /**
   * Returns a string with N amount of white-space
   *
   * @param count The count of white-space that needs
   *        to be returned
   * @return a string with the amount of white-space
   *         needed
   */
  def getWhiteSpace(count: Int): String =
    ("").padTo(count, ' ')

  /**
   * Prepends a Prefix to a String
   *
   * @param strings The list of strings to be prepended
   * @param prefix The string to be prepended to the start
   *        of each string
   * @return the List of converted Strings
   */
  def prependStrings(strings: List[String], prefix: String): List[String] =
    for {
      string <- strings
    } yield {
      prefix + string
    }

  /**
   * Appends a Suffix to a String
   *
   * @param strings The list of strings to be appended
   * @param suffix The string to be appended to the end
   *        of each string
   * @return the List of converted Strings
   */
  def appendStrings(strings: List[String], suffix: String): List[String] =
    for {
      string <- strings
    } yield {
      string + suffix
    }

  /**
   * Calculates whether or not the string passed is the
   * last string of a list.
   *
   * @param list The list of strings that need to 
   *        be tested
   * @param test The test string which needs to 
   *        be assessed
   * @return a boolean stating whether or not it is
   *         the last string 
   */
  def isLast(list: List[String], test: String): Boolean =
    if (list.last == test) true else false

  /**
   * Formats the file name from a self-desc schema into 
   * correct style and appeneds an '_1' to the string.
   *
   * @param name The 'maybe' unformatted file name which
   *        will be updated.
   * @return an updated file name string which can be used
   *         for both SQL and JsonPath files
   */
  def formatFileName(name: String): String =
    name.replaceAll("([^A-Z_])([A-Z])", "$1_$2").toLowerCase.concat("_1")

  /**
   * Builds a Schema name from variables.
   * 
   * @param info Information extracted from self-describing JSON Schema
   * @return a valid schema name
   */
  def getSchemaName(info: SelfDescInfo): String =
    "iglu:"+info.vendor+"/"+info.name+"/jsonschema/"+info.version

  /**
   * Create a Redshift Table name from a schema
   *
   * "iglu:com.acme/PascalCase/jsonschema/13-0-0" -> "com_acme_pascal_case_13"
   *
   * @param schema The Schema name
   * @return the Redshift Table name
   */
  def getTableName(schema: SelfDescInfo): String = {
    // Split the vendor's reversed domain name using underscores rather than dots
    val snakeCaseOrganization = schema.vendor.replaceAll( """\.""", "_").replaceAll("-", "_").toLowerCase

    // Change the name from PascalCase to snake_case if necessary
    val snakeCaseName = snakify(schema.name)

    // Extract the schemaver version's model
    val model = schema.version.split("-")(0)

    s"${snakeCaseOrganization}_${snakeCaseName}_${model}"
  }

  /**
   * Create a Redshift Table name from a file name
   *
   * "customerEvent.json" -> "customer_event"
   *
   * @param fileName file name with JSON Schema
   * @return the Redshift Table name
   */
  def getTableName(fileName: String): String = {
    val fileNameWithoutExtension =
      if (fileName.endsWith(".json")) fileName.dropRight(5)
      else fileName
    snakify(fileNameWithoutExtension)
  }

  /**
   * Transforms CamelCase string into snake_case
   * Also replaces all hyphens with underscores
   *
   * @param str string to transform
   * @return the underscored string
   */
  def snakify(str: String): String =
    str.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z\\d])([A-Z])", "$1_$2").replaceAll("-", "_").toLowerCase

  /**
   * Checks if comma-delimited string contains only integers (including negative)
   *
   * @param string string with items delimited by comma
   * @return true if string contains only integers
   */
  def isIntegerList(string: String): Boolean = {
    val elems = string.split(",").toList
    if (elems.length == 0) { false }
    else {
      elems.forall { s =>
        s.headOption match {
          case Some('-') if s.length > 1 => s.tail.forall(_.isDigit)
          case _ => s.forall(_.isDigit) }
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
