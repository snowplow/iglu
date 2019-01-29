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
package com.snowplowanalytics.iglu.server.model

import java.time.Instant

import cats.implicits._

import io.circe._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import io.circe.java8.time._
import io.circe.generic.semiauto._

import doobie._
import doobie.postgres.circe.json.implicits._

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaMap, SelfDescribingSchema}
import com.snowplowanalytics.iglu.core.circe.instances._

import Schema.Metadata

case class Schema(schemaMap: SchemaMap, metadata: Metadata, body: Json)

object Schema {

  // TODO: http
  val CanonicalUri = "com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0"

  case class Metadata(createdAt: Instant, updatedAt: Instant, isPublic: Boolean)

  object Metadata {
    implicit val metadataEncoder: Encoder[Metadata] =
      deriveEncoder[Metadata]

    implicit val metadataDecoder: Decoder[Metadata] =
      deriveDecoder[Metadata]
  }

  /** Encoding of a schema */
  sealed trait Repr
  object Repr {
    /** Canonical self-describing representation */
    case class Canonical(schema: SelfDescribingSchema[Json]) extends Repr
    /** Non-vanilla representation for UIs/non-validation clients */
    case class Full(schema: Schema) extends Repr
    /** Just URI string (but schema is on the server) */
    case class Uri(schemaKey: SchemaKey) extends Repr

    def apply(schema: Schema): Repr = Full(schema)
    def apply(uri: SchemaMap): Repr = Uri(uri.schemaKey)
    def apply(schema: SelfDescribingSchema[Json]): Repr = Canonical(schema)
  }

  sealed trait SchemaBody extends Product with Serializable
  object SchemaBody {
    case class SelfDescribing(schema: SelfDescribingSchema[Json]) extends SchemaBody
    case class BodyOnly(schema: Json) extends SchemaBody

    implicit val schemaBodyCirceDecoder: Decoder[SchemaBody] =
      Decoder.instance { json =>
        json.as[SelfDescribingSchema[Json]] match {
          case Right(schema) => SelfDescribing(schema).asRight
          case Left(_) => json.as[JsonObject].map(obj => BodyOnly(Json.fromJsonObject(obj)))
        }
      }
  }

  sealed trait Format extends Product with Serializable
  object Format {
    case object Jsonschema extends Format

    def parse(s: String): Option[Format] = s match {
      case "jsonschema" => Some(Jsonschema)
      case _ => None
    }
  }

  implicit val schemaEncoder: Encoder[Schema] =
    Encoder.instance { schema =>
      Json.obj("self" -> schema
        .schemaMap
        .asJson(encodeSchemaMap)
        .deepMerge(schema.metadata.asJson(Metadata.metadataEncoder))
      ).deepMerge(schema.body)
    }

  implicit val representationEncoder: Encoder[Repr] =
    Encoder.instance {
      case Repr.Full(s) => schemaEncoder.apply(s)
      case Repr.Uri(u) => Encoder[String].apply(u.toSchemaUri)
      case Repr.Canonical(s) => s.normalize.asObject match {
        case Some(obj) => Json.fromJsonObject(("$schema", CanonicalUri.asJson) +: obj)
        case None => s.normalize
      }
    }

  implicit val serverSchemaDecoder: Decoder[Schema] =
    Decoder.instance { cursor =>
      for {
        commonSelf <- cursor.downField("self").as[Json]
        self <- cursor.value.as[SchemaMap]
        meta <- commonSelf.as[Metadata]
        bodyJson <- cursor.as[JsonObject]
        body = bodyJson.toList.filterNot { case (key, _) => key == "self" }
      } yield Schema(self, meta, Json.fromFields(body))
    }

  implicit val schemaDoobieRead: Read[Schema] =
    Read[(SchemaMap, Metadata, Json)].map {
      case (schemaMap, meta, body) =>
        Schema(schemaMap, meta, body)
    }
}
