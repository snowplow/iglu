/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.iglu.server
package codecs

import scala.reflect.runtime.universe.typeOf

import cats.syntax.option._

import io.circe.Json

import org.http4s.rho.swagger.DefaultSwaggerFormats
import org.http4s.rho.swagger.models._

import com.snowplowanalytics.iglu.core.SchemaKey
import com.snowplowanalytics.iglu.server.model.IgluResponse
import com.snowplowanalytics.iglu.server.model.Schema

object Swagger {

  def Formats = DefaultSwaggerFormats
    .withSerializers(typeOf[IgluResponse], exampleModel)
    .withSerializers(typeOf[Schema.Repr.Canonical], exampleModel)
    .withSerializers(typeOf[JsonCodecs.JsonArrayStream[cats.effect.IO, Schema.Repr]], exampleModel)
    .withSerializers(typeOf[Json], exampleModel)
    .withSerializers(typeOf[SchemaKey], exampleModel)

  val exampleModel: Set[Model] = Set(
    ModelImpl(
      id = "IgluResponse",
      id2 = "IgluResponse",   // Somehow, only id2 works
      `type` = "object".some,
      description = "Iglu Server generic response".some,
      properties = Map(
        "message" -> StringProperty(
          required = true,
          description = "Human-readable message. The only required property for all kinds of responses".some,
          enums = Set()
        )
      ),
      example =
        """{"message" : "Schema does not exist"}""".some
    ),

    ModelImpl(
      id = "JsonArrayStream",
      id2 = "JsonArrayStream«F,Repr»",
      name = "Array".some,
      `type` = "array".some,
      description = "Generic JSON array JSON Schema representations".some,
      properties = Map( ),
      example =
        """[]""".some
    ),

    ModelImpl(
      id = "Canonical",
      id2 = "Canonical",
      `type` = "object".some,
      description = "Canonical representation of self-describing JSON Schema".some,
      properties = Map(
        "self" -> ObjectProperty(
          required = true,
          properties = Map("name" -> StringProperty(enums = Set()))
        )
      ),
      example =
        """{"self": {"name": "event", "vendor": "com.acme", "format": "jsonschema", "version": "1-0-0"}}""".some
    ),

    ModelImpl(
      id = "SchemaKey",
      id2 = "SchemaKey",
      `type` = "string".some,
      description = "Canonical iglu URI".some,
      properties = Map(),
      example =
        """iglu:com.snowplowanalytics/geo_location/jsonschema/1-0-0""".some
    ),

    ModelImpl(
      id = "Json",
      id2 = "Json",
      `type` = "string".some,
      description = "Any valid JSON".some,
      properties = Map(),
      example =
        """{"foo": null}""".some
    )
  )
}
