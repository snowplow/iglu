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

import org.json4s.JsonAST._

import com.snowplowanalytics.iglu.schemaddl.jsonschema.{CommonProperties, Schema, StringProperties}

object Suggestion {

  val stringSuggestion: Suggestion = (schema, required) =>
    schema.`type` match {
      case Some(CommonProperties.String) =>
        Some(name => Field(name, Type.String, Mode.required(required)))
      case Some(types) if types.nullable(CommonProperties.String) =>
        Some(name => Field(name, Type.String, Mode.Nullable) )
      case _ => None
    }

  val booleanSuggestion: Suggestion = (schema, required) =>
    schema.`type` match {
      case Some(CommonProperties.Boolean) =>
        Some(name => Field(name, Type.Boolean, Mode.required(required)))
      case Some(CommonProperties.Product(types)) if withNull(types, CommonProperties.Boolean) =>
        Some(name => Field(name, Type.Boolean, Mode.Nullable))
      case _ => None
    }

  val integerSuggestion: Suggestion = (schema, required) =>
    schema.`type` match {
      case Some(CommonProperties.Integer) =>
        Some(name => Field(name, Type.Integer, Mode.required(required)))
      case Some(CommonProperties.Product(types)) if withNull(types, CommonProperties.Integer) =>
        Some(name => Field(name, Type.Integer, Mode.Nullable))
      case _ => None
    }

  val floatSuggestion: Suggestion = (schema, required) =>
    schema.`type` match {
      case Some(CommonProperties.Number) =>
        Some(name => Field(name, Type.Float, Mode.required(required)))
      case Some(CommonProperties.Product(types)) if onlyNumeric(types.toSet, true) =>
        Some(name => Field(name, Type.Float, Mode.Nullable))
      case Some(CommonProperties.Product(types)) if onlyNumeric(types.toSet, false)  =>
        Some(name => Field(name, Type.Float, Mode.required(required)))
      case Some(CommonProperties.Product(types)) if withNull(types, CommonProperties.Number) =>
        Some(name => Field(name, Type.Float, Mode.Nullable))
      case _ => None
    }

  val complexEnumSuggestion: Suggestion = (schema, required) =>
    schema.enum match {
      case Some(CommonProperties.Enum(values)) =>
        Some(fromEnum(values, required))
      case _ => None
    }

  // `date-time` format usually means zoned format, which corresponds to BQ Timestamp
  val timestampSuggestion: Suggestion = (schema, required) =>
    (schema.`type`, schema.format) match {
      case (Some(CommonProperties.String), Some(StringProperties.DateFormat)) =>
        Some(name => Field(name, Type.Date, Mode.required(required)))
      case (Some(CommonProperties.Product(types)), Some(StringProperties.DateFormat)) if withNull(types, CommonProperties.String) =>
        Some(name => Field(name, Type.Date, Mode.Nullable))

      case (Some(CommonProperties.String), Some(StringProperties.DateTimeFormat)) =>
        Some(name => Field(name, Type.Timestamp, Mode.required(required)))
      case (Some(CommonProperties.Product(types)), Some(StringProperties.DateTimeFormat)) if withNull(types, CommonProperties.String) =>
        Some(name => Field(name, Type.Timestamp, Mode.Nullable))

      case _ => None
    }

  def finalSuggestion(schema: Schema, required: Boolean): String => Field =
    schema.`type` match {
      case Some(jsonType) if jsonType.nullable =>
        name => Field(name, Type.String, Mode.Nullable)
      case _ =>
        name => Field(name, Type.String, Mode.required(required))
    }

  val suggestions: List[Suggestion] = List(
    timestampSuggestion,
    booleanSuggestion,
    stringSuggestion,
    integerSuggestion,
    floatSuggestion,
    complexEnumSuggestion
  )

  private[iglu] def fromEnum(enums: List[JValue], required: Boolean): String => Field = {
    def isString(json: JValue) = json.isInstanceOf[JString] || json == JNull
    def isInteger(json: JValue) = json.isInstanceOf[JInt] || json == JNull
    def isNumeric(json: JValue) =
      json.isInstanceOf[JInt] || json.isInstanceOf[JDouble] || json.isInstanceOf[JDecimal] || json == JNull
    val noNull: Boolean = !enums.contains(JNull)

    if (enums.forall(isString)) {
      name => Field(name, Type.String, Mode.required(required && noNull))
    } else if (enums.forall(isInteger)) {
      name => Field(name, Type.Integer, Mode.required(required && noNull))
    } else if (enums.forall(isNumeric)) {
      name => Field(name, Type.Float, Mode.required(required && noNull))
    } else {
      name => Field(name, Type.String, Mode.required(required && noNull))
    }
  }

  private def withNull(types: List[CommonProperties.Type], t: CommonProperties.Type): Boolean =
    types.toSet == Set(t, CommonProperties.Null) || types == List(t)

  private def onlyNumeric(types: Set[CommonProperties.Type], allowNull: Boolean): Boolean =
    if (allowNull) types == Set(CommonProperties.Number, CommonProperties.Integer, CommonProperties.Null)
    else types == Set(CommonProperties.Number, CommonProperties.Integer)
}
