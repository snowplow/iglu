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

import cats.syntax.either._

import io.circe.{ Json, Decoder }
import io.circe.generic.semiauto._

trait CirceToSchema extends StringDecoders with NumberDecoders with ObjectDecoders with ArrayDecoders with CommonDecoders {

  implicit val schemaDecoder: Decoder[Schema] = deriveDecoder[Schema]

  implicit val toSchema: ToSchema[Json] = new ToSchema[Json] {
    def parse(json: Json): Option[Schema] =
      json.as[Schema].toOption
  }
}
