/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.schemaddl.bigquery

import com.snowplowanalytics.iglu.schemaddl.StringUtils

/**
  * Type-safe AST proxy to `com.google.cloud.bigquery.Field` schema type
  * Up to client's code to convert it
  */
case class Field(name: String, fieldType: Type, mode: Mode) {
  def setMode(mode: Mode): Field = this match {
    case Field(n, t, _) => Field(n, t, mode)
  }

  def normalName: String =
    StringUtils.snakeCase(name)

  def normalized: Field = fieldType match {
    case Type.Record(fields) => Field(normalName, Type.Record(fields.map(_.normalized)), mode)
    case other => Field(normalName, other, mode)
  }
}
