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

// This project
import DdlGenerator._

/**
 * Converts lists of keys into a JsonPath file.
 */
object JsonPathGenerator {

  private object JsonPathPrefix {
    val Schema    = "$.schema."
    val Hierarchy = "$.hierarchy."
    val Data      = "$.data."
  }

  private val JsonPathSchemaFields = List(
    "vendor",
    "name",
    "format",
    "version"
  )

  private val JsonPathHierarchyFields = List(
    "rootId",
    "rootTstamp",
    "refRoot",
    "refTree",
    "refParent"
  )

  private val JsonPathFileHeader = List(
    "{",
    "    \"jsonpaths\": ["
  )

  private val JsonPathFileFooter = List(
    "    ]",
    "}"
  )

  /**
   * Returns a validated JsonPath file based on the list of DDL columns.
   * This function should be tied to constructed CreateTable's DDL to preserve
   * correct order of columns (for example they could be rearranged).
   *
   * @param columns ordered list of table columns
   * @param rawMode decide whether snowplow-specific columns expected
   * @return a JsonPath String containing all of the relevant fields
   */
  def getJsonPathsFile(columns: List[Column], rawMode: Boolean = false): String = {
    val columnNames: List[String] =
      if (rawMode) { columns.map(JsonPathPrefix.Data + _.columnName) }    // everything is data in raw mode
      else {                                                              // add schema and hierarchy otherwise
        val dataColumns = columns.filterNot(selfDescSchemaColumns.contains(_))
                                 .filterNot(parentageColumns.contains(_))
                                 .map(_.columnName)

        val schemaFieldList    = JsonPathSchemaFields.map(JsonPathPrefix.Schema + _)
        val hierarchyFieldList = JsonPathHierarchyFields.map(JsonPathPrefix.Hierarchy + _)
        val dataFieldList      = dataColumns.map(JsonPathPrefix.Data + _)

        schemaFieldList ++ hierarchyFieldList ++ dataFieldList
      }

    (JsonPathFileHeader ++ formatFields(columnNames) ++ JsonPathFileFooter).mkString("\n")
  }

  /**
   * Adds whitespace to the front of each string in the list for formatting
   * purposes.
   *
   * @param fields The fields that need to have white space added
   * @return the formatted fields
   */
  private[schemaddl] def formatFields(fields: List[String]): List[String] = {
    val prefix = "".padTo(8, ' ')
    for {
      field <- fields
    } yield {
      val suffix = if (isLast(fields, field)) "" else ","
      prefix + "\"" + field + "\"" + suffix
    }
  }

  /**
   * Calculates whether or not the string passed is the last string of a list
   *
   * @param list The list of strings that need to be tested
   * @param test The test string which needs to be assessed
   * @return a boolean stating whether or not it is the last string
   */
  private[schemaddl] def isLast(list: List[String], test: String): Boolean =
    if (list.last == test) true else false
}
