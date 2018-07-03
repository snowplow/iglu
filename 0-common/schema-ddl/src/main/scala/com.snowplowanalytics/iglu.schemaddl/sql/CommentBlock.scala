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

/**
 * Class representing comment block in Ddl file
 * Can be rendered into file along with other Ddl-statements
 *
 * @param lines sequence of lines
 * @param prepend optional amount of spaces to prepend delimiter (--)
 */
case class CommentBlock(lines: Vector[String], prepend: Int = 0) extends Statement {
  import CommentBlock._

  override val separator = ""

  def toDdl = lines.map(l => "--" + emptyOrSpace(l)).mkString("\n")
}

object CommentBlock {
  def apply(line: String, prepend: Int): CommentBlock =
    CommentBlock(Vector(line), prepend)

  /**
   * Don't prepend empty strings with space
   */
  private def emptyOrSpace(line: String): String =
    if (line.nonEmpty) s" $line"
    else ""
}
