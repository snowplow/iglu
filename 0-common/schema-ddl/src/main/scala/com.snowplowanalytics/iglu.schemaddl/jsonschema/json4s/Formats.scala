/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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

object Formats {
  /**
    * json4s formats for all JSON Schema properties
    */
  implicit lazy val allFormats: org.json4s.Formats =
    org.json4s.DefaultFormats ++ List(
      StringSerializers.FormatSerializer,
      StringSerializers.MinLengthSerializer,
      StringSerializers.MaxLengthSerializer,
      StringSerializers.PatternSerializer,
      ObjectSerializers.PropertiesSerializer,
      ObjectSerializers.AdditionalPropertiesSerializer,
      ObjectSerializers.RequiredSerializer,
      ObjectSerializers.PatternPropertiesSerializer,
      CommonSerializers.TypeSerializer,
      CommonSerializers.EnumSerializer,
      CommonSerializers.OneOfSerializer,
      CommonSerializers.DescriptionSerializer,
      NumberSerializers.MaximumSerializer,
      NumberSerializers.MinimumSerializer,
      NumberSerializers.MultipleOfSerializer,
      ArraySerializers.AdditionalPropertiesSerializer,
      ArraySerializers.MaxItemsSerializer,
      ArraySerializers.MinItemsSerializer,
      ArraySerializers.ItemsSerializer)
}
