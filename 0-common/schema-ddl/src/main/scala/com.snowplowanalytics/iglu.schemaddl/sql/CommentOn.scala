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
 * COMMENT ON
 * { TABLE object_name | COLUMN object_name.column_name |
 * CONSTRAINT constraint_name ON table_name |
 * DATABASE object_name |
 * VIEW object_name }
 * IS 'text'
 */
case class CommentOn(tableName: String, comment: String) extends Statement {
  override val separator = ";"
  def toDdl = s"COMMENT ON TABLE $tableName IS '$comment'"
}
