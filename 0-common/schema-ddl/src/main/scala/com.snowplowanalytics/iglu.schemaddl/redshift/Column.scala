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
 * Class holding all information about Redshift's column
 *
 * @param columnName column_name
 * @param dataType data_type such as INTEGER, VARCHAR, etc
 * @param columnAttributes set of column_attributes such as ENCODE
 * @param columnConstraints set of column_constraints such as NOT NULL
 */
case class Column(
  columnName: String,
  dataType: DataType,
  columnAttributes: Set[ColumnAttribute] = Set.empty[ColumnAttribute],
  columnConstraints: Set[ColumnConstraint] = Set.empty[ColumnConstraint]
) extends Ddl {

  /**
   * Formatted column's DDL
   * Calling method must provide length for each tab via Tuple5
   *
   * @param tabs tuple of lengths (prepend, table_name, data_type, etc)
   * @return formatted DDL
   */
  def toFormattedDdl(tabs: (Int, Int, Int, Int, Int)): String =
    withTabs(tabs._1, " ") +
      withTabs(tabs._2, nameDdl) +
      withTabs(tabs._3, dataTypeDdl) +
      withTabs(tabs._4, attributesDdl) +
      withTabs(tabs._5, constraintsDdl)

  /**
   * Compact way to output column
   *
   * @return string representing column without formatting
   */
  def toDdl = toFormattedDdl((1, 1, 1, 1, 1))

  // Get warnings only from data types suggestions
  override val warnings = dataType.warnings

  /**
   * column_name ready to output with surrounding quotes to prevent odd chars
   * from breaking the table
   */
  val nameDdl = "\"" + columnName + "\" "

  /**
   * data_type ready to output
   */
  val dataTypeDdl = dataType.toDdl

  /**
   * column_attributes ready to output if exists
   */
  val attributesDdl = columnAttributes.map(" " + _.toDdl).mkString(" ")

  /**
   * column_constraints ready to output if exists
   */
  val constraintsDdl = columnConstraints.map(" " + _.toDdl).mkString(" ")
}
