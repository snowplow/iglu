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
 * Trait for *independent* SQL DDL statements.
 * Unlike simple Ddl objects, these can be used as stand-alone
 * commands and be content of file.
 * We're always using semicolon in the end of statements
 */

trait Statement extends Ddl with Product with Serializable {
  /**
   * Symbol used to separate statement from other.
   * Usually it is a semicolon, however special statements, like
   * empty line or comment don't use separators
   * Container class (not Statement) handles separators as well as newlines
   */
  val separator: String = ";"

  /**
   * Properly render statement with separator
   * Use it instead `toDdl` on Statement objects
   */
  def render: String =
    toDdl + separator
}
