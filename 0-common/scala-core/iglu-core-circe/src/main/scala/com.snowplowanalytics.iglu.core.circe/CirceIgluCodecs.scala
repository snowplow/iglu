/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.core.circe

// Cats
import cats.data._
import cats.instances.option._
import cats.syntax.cartesian._
import cats.syntax.either._

// Circe
import io.circe._
import io.circe.syntax._

// This library
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.Containers._

/**
 * Example of Circe codecs for Iglu entities
 */
object CirceIgluCodecs {

  val decodeSchemaVer: Decoder[SchemaVer] =
    Decoder.instance(parseSchemaVer)

  val encodeSchemaVer: Encoder[SchemaVer] =
    Encoder.instance { schemaVer =>
      Json.fromString(schemaVer.asString)
    }

  val decodeSchemaKey: Decoder[SchemaKey] =
    Decoder.instance(parseSchemaKey)

  val encodeSchemaKey: Encoder[SchemaKey] =
    Encoder.instance { key =>
      Json.obj(
        "vendor"  -> Json.fromString(key.vendor),
        "name"    -> Json.fromString(key.name),
        "format"  -> Json.fromString(key.format),
        "version" -> Json.fromString(key.version.asString)
      )
    }

  val encodeSchema: Encoder[SelfDescribingSchema[Json]] =
    Encoder.instance { schema =>
      Json.obj("self" -> schema.self.asJson(encodeSchemaKey)).deepMerge(schema.schema)
    }

  val encodeData: Encoder[SelfDescribingData[Json]] =
    Encoder.instance { data =>
      Json.obj("schema" -> Json.fromString(data.schema.toSchemaUri), "data" -> data.data)
    }

  private[circe] def parseSchemaVer(hCursor: HCursor): Either[DecodingFailure, SchemaVer] =
    for {
      jsonString <- hCursor.as[String]
      parsed     = SchemaVer.parse(jsonString)
      schemaVer  <- Either.fromOption(parsed, DecodingFailure("SchemaVer is missing", hCursor.history))
    } yield schemaVer

  private[circe] def parseSchemaKey(hCursor: HCursor): Either[DecodingFailure, SchemaKey] =
    for {
      selfMap   <- hCursor.as[JsonObject].map(_.toMap)
      schemaKey <- selfMapToSchemaKey(selfMap, hCursor)
    } yield schemaKey

  private[circe] def selfMapToSchemaKey(selfMap: Map[String, Json], hCursor: HCursor): Either[DecodingFailure, SchemaKey] = {
    val self = (
      selfMap.get("vendor") |@| selfMap.get("name") |@| selfMap.get("format") |@| selfMap.get("version")).map { (v, n, f, ver) =>
      for {
        vendor  <- v.asString
        name    <- n.asString
        format  <- f.asString
        version <- ver.as(decodeSchemaVer).toOption
      } yield SchemaKey(vendor, name, format, version)
    }

    Either.fromOption(self.flatten, DecodingFailure("SchemaKey has incompatible format", hCursor.history))
  }
}
