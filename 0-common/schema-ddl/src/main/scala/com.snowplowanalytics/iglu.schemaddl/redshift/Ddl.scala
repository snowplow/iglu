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
package redshift

/**
 * Base class for everything that can be represented as Redshift DDL
 */
trait Ddl {
  /**
   * Output actual DDL as string
   *
   * @return valid DDL
   */
  def toDdl: String

  /**
   * Aggregates all warnings from child elements
   */
  val warnings: List[String] = Nil

  /**
   * Append specified amount of ``spaces`` to the string to produce formatted DDL
   *
   * @param spaces amount of spaces
   * @param str string itself
   * @return string with spaces
   */
  def withTabs(spaces: Int, str: String): String =
    if (str.length == 0) " " * spaces
    else if (spaces <= str.length) str
    else str + (" " * (spaces - str.length))
}

