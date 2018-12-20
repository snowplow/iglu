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
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.{CommonProperties,ArrayProperty}

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

object Field {
  def build(name: String, topSchema: Schema, required: Boolean): Field = {
    topSchema.`type` match {
      case Some(types) if types.possiblyWithNull(CommonProperties.Type.Object) =>
        val subfields = topSchema.properties.map(_.value).getOrElse(Map.empty)
        if (subfields.isEmpty) {
          Suggestion.finalSuggestion(topSchema, required)(name)
        } else {
          val requiredKeys = topSchema.required.toList.flatMap(_.value)
          val fields = subfields.map { case (key, schema) =>
            build(key, schema, requiredKeys.contains(key))
          }
          val subFields = fields.toList.sortBy(field => (Mode.sort(field.mode), field.name))
          Field(name, Type.Record(subFields), Mode.required(required))
        }
      case Some(CommonProperties.Type.Array) =>
        topSchema.items match {
          case Some(ArrayProperty.Items.ListItems(schema)) =>
            build(name, schema, false).copy(mode = Mode.Repeated)
          case _ =>
            Suggestion.finalSuggestion(topSchema, required)(name)
        }
      case _ =>
        Suggestion.suggestions
          .find(suggestion => suggestion(topSchema, required).isDefined)
          .flatMap(_.apply(topSchema, required))
          .getOrElse(Suggestion.finalSuggestion(topSchema, required))
          .apply(name)
    }
  }
}
