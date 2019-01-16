/*
 * Copyright (c) 2016-2017 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.core
package circe

import cats.{ Show, Eq }
import cats.syntax.either._

import io.circe._

import com.snowplowanalytics.iglu.core.typeclasses._

trait instances {
  final implicit val igluAttachToDataCirce: ExtractSchemaKey[Json] with ToData[Json] with ExtractSchemaKey[Json] =
    new ExtractSchemaKey[Json] with ToData[Json] {

      def extractSchemaKey(entity: Json) =
        for {
          jsonSchema <- entity.asObject.toRight(ParseError.InvalidData)
          schemaUri <- jsonSchema.toMap.get("schema").flatMap(_.asString).toRight(ParseError.InvalidData)
          schemaKey <- SchemaKey.fromUri(schemaUri)
        } yield schemaKey

      def getContent(json: Json): Either[ParseError, Json] =
      json.asObject.flatMap(_.apply("data")).toRight(ParseError.InvalidData)
    }

  final implicit val igluAttachToSchema: ToSchema[Json] with ExtractSchemaMap[Json] =
    new ToSchema[Json] with ExtractSchemaMap[Json] {

      def extractSchemaMap(entity: Json): Either[ParseError, SchemaMap] = {
        CirceIgluCodecs.parseSchemaMap(entity.hcursor) match {
          case Right(r) => Right(r)
          case Left(err) => ParseError.parse(err.message) match {
            case Some(parseError) => Left(parseError)
            case None => Left(ParseError.InvalidSchema)
          }
        }

      }

      def getContent(schema: Json): Json =
        Json.fromJsonObject {
          JsonObject.fromMap {
            schema.asObject.map(_.toMap.filterKeys(_ != "self")).getOrElse(Map.empty)
          }
        }
    }

  // Container-specific instances

  final implicit val igluNormalizeDataJson: NormalizeData[Json] =
    new NormalizeData[Json] {
      override def normalize(container: SelfDescribingData[Json]): Json =
        CirceIgluCodecs.encodeData(container)
    }

  final implicit val igluNormalizeSchemaJson: NormalizeSchema[Json] =
    new NormalizeSchema[Json] {
      override def normalize(container: SelfDescribingSchema[Json]): Json =
        CirceIgluCodecs.encodeSchema(container)
    }

  final implicit val igluStringifyDataJson: StringifyData[Json] =
    new StringifyData[Json] {
      override def asString(container: SelfDescribingData[Json]): String =
        container.normalize(igluNormalizeDataJson).noSpaces
    }

  final implicit val igluStringifySchemaJson: StringifySchema[Json] =
    new StringifySchema[Json] {
      override def asString(container: SelfDescribingSchema[Json]): String =
        container.normalize(igluNormalizeSchemaJson).noSpaces
    }

  // Cats instances

  final implicit val schemaVerShow: Show[SchemaVer] =
    Show.show(_.asString)

  final implicit val schemaKeyShow: Show[SchemaKey] =
    Show.show(_.toSchemaUri)

  final implicit val partialSchemaKeyShow: Show[PartialSchemaKey] =
    Show.show(_.toSchemaUri)

  final implicit val schemaVerEq: Eq[SchemaVer.Full] =
    Eq.fromUniversalEquals[SchemaVer.Full]

  // Decide if we want to provide Eq partial
  final implicit val schemaKeyEq: Eq[SchemaKey] =
    Eq.fromUniversalEquals[SchemaKey]
}

object instances extends instances with CirceIgluCodecs
