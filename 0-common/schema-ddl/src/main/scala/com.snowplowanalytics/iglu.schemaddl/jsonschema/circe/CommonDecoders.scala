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

import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._

trait CommonDecoders {
  implicit def schemaDecoder: Decoder[Schema]

  implicit val typeDecoder = Decoder[String]
    .emap(Type.fromString)
    .or(Decoder[List[String]].emap(Type.fromProduct))

  implicit val descriptionDecoder = Decoder[String].map(Description)

  implicit lazy val enumDecoder = Decoder[List[Json]].map(Enum.apply)

  implicit lazy val oneOfDecoder = Decoder[List[Schema]].map(OneOf.apply)
}
