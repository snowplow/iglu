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
package com.snowplowanalytics.iglu.server.codecs

import cats.effect.Sync

import fs2.text.utf8Encode
import fs2.{RaiseThrowable, Stream}

import io.circe.Json
import io.circe.fs2.byteStreamParser

import org.http4s.circe._
import org.http4s.{Entity, EntityEncoder, Headers}

import com.snowplowanalytics.iglu.core.{ SelfDescribingSchema, SchemaKey }
import com.snowplowanalytics.iglu.core.circe.CirceIgluCodecs._
import com.snowplowanalytics.iglu.server.model.{IgluResponse, Schema, SchemaDraft}
import com.snowplowanalytics.iglu.server.service.MetaService.ServerInfo


trait JsonCodecs {

  case class JsonArrayStream[F[_], A](nonArray: Stream[F, A])

  implicit def arrayStreamEncoder[F[_]: RaiseThrowable, A: EntityEncoder[F, ?]] = {
    val W = implicitly[EntityEncoder[F, A]]
    new EntityEncoder[F, JsonArrayStream[F, A]] {
      override def toEntity(stream: JsonArrayStream[F, A]): Entity[F] = {
        val commaSeparated = stream.nonArray.flatMap(W.toEntity(_).body).through(byteStreamParser).map(_.noSpaces).intersperse(",")
        val wrapped = Stream.emit("[") ++ commaSeparated ++ Stream.emit("]")
        Entity(wrapped.through(utf8Encode))
      }

      override def headers: Headers = W.headers
    }
  }

  implicit def representationEntityDecoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityDecoder[F, List[Schema]]

  implicit def representationEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, Schema.Repr]

  implicit def representationListEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, List[Schema.Repr]]

  implicit def stringArrayEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, List[String]]

  implicit def schemaEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, Schema]

  implicit def igluResponseEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, IgluResponse]

  implicit def schemaListEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, List[Schema]]

  implicit def selfDescribingSchemaEntityEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, SelfDescribingSchema[Json]]

  implicit def selfDescribingSchemaEntityDecoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityDecoder[F, SelfDescribingSchema[Json]]

  implicit def selfDescribingSchemaListEntityDecoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityDecoder[F, List[SelfDescribingSchema[Json]]]

  implicit def igluResponseEntityDecoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityDecoder[F, IgluResponse]

  implicit def draftHttpEncoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, SchemaDraft]

  implicit def serverInfoEntityEcoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityEncoder[F, ServerInfo]

  implicit def schemaKeyListEntityDecoder[F[_]: Sync] =
    CirceEntityCodec.circeEntityDecoder[F, List[SchemaKey]]
}

object JsonCodecs extends JsonCodecs
