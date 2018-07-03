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
package com.snowplowanalytics.iglu.schemaddl.sql
package generators

/**
 * File representing sequence of valid DDL statements able to be printed
 *
 * @todo Redshift table definition is tightly coupled with JSONPath file
 *       and client code needs to reprocess it to have same order, but
 *       different column names
 *
 * @param statements sequence of valid independent DDL statements
 */
case class DdlFile(statements: List[Statement]) {
  import DdlFile._

  /**
   * Convert content of file into string
   *
   * @return most concise string representation
   */
  def render: String = render(List(formatAlterTable, formatCommentOn))

  /**
   * Convert content of file into string using list of predefined formatters
   * WARNING: setting delimiters is on behalf of formatters,
   * so empty `formatters` list produce invalid output
   *
   * @param formatters list of partial functions applicable for some subsets of DDL statements
   * @return formatted representation
   */
  def render(formatters: List[StatementFormatter]): String = {
    val format: Statement => String = chooseFormatter(formatters)
    val formatted = statements.foldLeft(List.empty[String]) { (acc, cur) => format(cur) :: acc }
    formatted.reverse.mkString("\n")
  }

  /**
   * Aggregates all warnings from child statements
   */
  def warnings: List[String] = statements.flatMap(_.warnings)
}

object DdlFile {

  /**
   * Type alias representing some partial function able to format particular DDL statement
   */
  // TODO: refactor formatters being able to format whole File, not just Statement
  type StatementFormatter = PartialFunction[Statement, String]

  /**
   * Helper function choosing first formatter defined on some statement type
   *
   * @param formatters list of partial functions able to reformat statement
   * @param statement actual DDL statement
   * @return string representation
   */
  def chooseFormatter(formatters: List[StatementFormatter])(statement: Statement): String =
    formatters
      .find(_.isDefinedAt(statement))
      .map(formatter => formatter(statement))
      .getOrElse(statement.toDdl + statement.separator)

  // TODO: formatter shouldn't know anything about actual string-representation, only spaces and newlines
  val formatAlterTable: StatementFormatter = {
    case ddl: AlterTable =>
      s"  ALTER TABLE ${ddl.tableName}\n    ${ddl.statement.toDdl};"
  }

  val formatCommentOn: StatementFormatter = {
    case ddl: CommentOn => s"  ${ddl.toDdl};"
  }
}
