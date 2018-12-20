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

import cats.implicits._

import io.circe._

import properties.ArrayProperty._

trait ArrayDecoders {

  implicit def schemaDecoder: Decoder[Schema]

  implicit val itemsDecoder: Decoder[Items] = Decoder.instance { cursor =>
    cursor
      .as[List[Schema]]
      .map(Items.TupleItems)
      .orElse[DecodingFailure, Items](cursor.as[Schema].map(Items.ListItems))
  }

  implicit val additionalItemsDecoder: Decoder[AdditionalItems] = Decoder.instance { cursor =>
    cursor
      .as[Schema]
      .map(AdditionalItems.AdditionalItemsSchema.apply)
      .orElse[DecodingFailure, AdditionalItems](cursor.as[Boolean].map(AdditionalItems.AdditionalItemsAllowed))
  }

  implicit val maxItemsDecoder: Decoder[MaxItems] = Decoder.instance { cursor =>
    cursor
      .value
      .asNumber
      .flatMap(_.toBigInt)
      .toRight(DecodingFailure("maxItems expected to be a natural number", cursor.history))
      .map(MaxItems.apply)
  }

  implicit val minItemsDecoder: Decoder[MinItems] = Decoder.instance { cursor =>
    cursor
      .value
      .asNumber
      .flatMap(_.toBigInt)
      .toRight(DecodingFailure("minItems expected to be a natural number", cursor.history))
      .map(MinItems.apply)
  }
}
