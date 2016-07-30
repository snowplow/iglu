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

/**
 * Class holding all information about Redshift's table
 *
 * @param tableName table_name
 * @param columns iterable of all columns DDLs
 * @param tableConstraints set of table_constraints such as PRIMARY KEY
 * @param tableAttributes set of table_attributes such as DISTSTYLE
 */
case class CreateTable(
  tableName: String,
  columns: List[Column],
  tableConstraints: Set[TableConstraint] = Set.empty[TableConstraint],
  tableAttributes: Set[TableAttribute] = Set.empty[TableAttribute]
) extends Statement {

  def toDdl = {
    val columnsDdl = columns.map(_.toFormattedDdl(tabulation)
      .replaceAll("\\s+$", ""))
      .mkString(",\n")
    s"""CREATE TABLE IF NOT EXISTS $tableName (
        |$columnsDdl$getConstraints
        |)$getAttributes""".stripMargin
  }

  // Collect warnings from every column
  override val warnings = columns.flatMap(_.warnings)

  // Tuple with lengths of each column in formatted DDL file
  private val tabulation = {
    def getLength(f: Column => Int): Int =
      columns.foldLeft(0)((acc, b) => if (acc > f(b)) acc else f(b))

    val prepend = 4
    val first = getLength(_.nameDdl.length)
    val second = getLength(_.dataType.toDdl.length)
    val third = getLength(_.attributesDdl.length)
    val fourth = getLength(_.constraintsDdl.length)

    (prepend, first, second, third, fourth)
  }

  /**
   * Format constraints for table
   *
   * @return string with formatted table_constaints
   */
  private def getConstraints: String = {
    if (tableConstraints.isEmpty) ""
    else ",\n" + tableConstraints.map(c => withTabs(tabulation._1, " ") + c.toDdl).

      mkString("\n")
  }
  /**
   * Format attributes for table
   *
   * @return string with formatted table_attributes
   */
  private def getAttributes: String = {
    if (tableConstraints.isEmpty) ""
    else "\n" + tableAttributes.map(_.toDdl).

      mkString("\n")
  }
}

