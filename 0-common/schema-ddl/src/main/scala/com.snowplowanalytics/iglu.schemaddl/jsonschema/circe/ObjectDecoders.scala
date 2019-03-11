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
package circe

import io.circe._

import cats.implicits._

import properties.ObjectProperty._

trait ObjectDecoders {
  private val PropertiesExpectation = "properties expected to be a map of JSON schemas"
  private val PatternPropertiesExpectation = "patternProperties expected to be a map of JSON schemas"
  private val RequiredExpectation = "required expected to be an array of keys"

  implicit def schemaDecoder[A]: Decoder[Schema[A]]

  implicit def propertiesDecoder[A](implicit adecoder: Decoder[A]): Decoder[Properties[A]] = Decoder.instance { cursor =>
    cursor.value.fold(
      DecodingFailure(s"$PropertiesExpectation, null found", cursor.history).asLeft,
      _ => DecodingFailure(s"$PropertiesExpectation, boolean found", cursor.history).asLeft,
      num => DecodingFailure(s"$PropertiesExpectation, number $num found", cursor.history).asLeft,
      str => DecodingFailure(s"$PropertiesExpectation, string $str found", cursor.history).asLeft,
      arr => DecodingFailure(s"$PropertiesExpectation, string $arr found", cursor.history).asLeft,
      obj =>
        obj.toList
          .traverse[Decoder.Result, (String, A)] { case (property, json) => json.as[A].map((property, _)) }
          .map(m => Properties.apply(m.toMap))
    )
  }

  implicit def additionalPropertiesDecoder[A](implicit adecoder: Decoder[A]): Decoder[AdditionalProperties[A]] = Decoder.instance { cursor =>
    cursor
      .as[A]
      .map(AdditionalProperties.AdditionalPropertiesSchema[A])
      .orElse[DecodingFailure, AdditionalProperties[A]](cursor.as[Boolean].map(AdditionalProperties.AdditionalPropertiesAllowed(_)))
  }

  implicit val requiredDecoder: Decoder[Required] = Decoder.instance { cursor =>
    cursor.value.fold(
      DecodingFailure(s"$RequiredExpectation, null found", cursor.history).asLeft,
      _ => DecodingFailure(s"$RequiredExpectation, boolean found", cursor.history).asLeft,
      num => DecodingFailure(s"$RequiredExpectation, number $num found", cursor.history).asLeft,
      str => DecodingFailure(s"$RequiredExpectation, string $str found", cursor.history).asLeft,
      arr => arr.map(_.as[String]).sequence[Decoder.Result, String].map(_.toList).map(Required.apply),
      _ => DecodingFailure(s"$RequiredExpectation, object found", cursor.history).asLeft
    )
  }

  implicit def patternPropertiesDecoder[A](implicit adecoder: Decoder[A]): Decoder[PatternProperties[A]] = Decoder.instance { cursor =>
    cursor.value.fold(
      DecodingFailure(s"$PatternPropertiesExpectation, null found", cursor.history).asLeft,
      _ => DecodingFailure(s"$PatternPropertiesExpectation, boolean found", cursor.history).asLeft,
      num => DecodingFailure(s"$PatternPropertiesExpectation, number $num found", cursor.history).asLeft,
      str => DecodingFailure(s"$PatternPropertiesExpectation, string $str found", cursor.history).asLeft,
      arr => DecodingFailure(s"$PatternPropertiesExpectation, string $arr found", cursor.history).asLeft,
      obj =>
        obj.toList
          .traverse[Decoder.Result, (String, A)] { case (property, json) => json.as[A].map((property, _)) }
          .map(m => PatternProperties.apply(m.toMap))
    )
  }
}
