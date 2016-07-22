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
package com.snowplowanalytics.iglu.schemaddl.jsonschema
package json4s

// json4s
import org.json4s.{ JValue, Extraction }

/**
 * Module containing type class instance with all Schema properties' Serializers
 */
object Json4sFromSchema {
  /**
   * json4s formats for all JSON Schema properties
   */
  implicit val allFormats =
    org.json4s.DefaultFormats +
      StringSerializers.FormatSerializer +
      StringSerializers.MinLengthSerializer +
      StringSerializers.MaxLengthSerializer +
      StringSerializers.PatternSerializer +
      ObjectSerializers.PropertiesSerializer +
      ObjectSerializers.AdditionalPropertiesSerializer +
      ObjectSerializers.RequiredSerializer +
      ObjectSerializers.PatternPropertiesSerializer +
      CommonSerializers.TypeSerializer +
      CommonSerializers.EnumSerializer +
      CommonSerializers.OneOfSerializer +
      NumberSerializers.MaximumSerializer +
      NumberSerializers.MinimumSerializer +
      NumberSerializers.MultipleOfSerializer +
      ArraySerializers.AdditionalPropertiesSerializer +
      ArraySerializers.MaxItemsSerializer +
      ArraySerializers.MinItemsSerializer +
      ArraySerializers.ItemsSerializer

  /**
   * Type class instance allowing to convert [[Schema]] to JValue
   *
   * So far this is single implementation, but still need
   * to be imported into scope to get Schema.parse method work
   */
  implicit object Json4sFromSchema extends FromSchema[JValue] {
    def normalize(schema: Schema): JValue =
      Extraction.decompose(schema)
  }
}
