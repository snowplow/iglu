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

import io.circe._

import com.snowplowanalytics.iglu.core.typeclasses._

trait instances {
  final implicit val igluAttachToDataCirce: AttachSchemaKey[Json] with ToData[Json] with ExtractSchemaKey[Json] =
    new AttachSchemaKey[Json] with ToData[Json] {

      def extractSchemaKey(entity: Json): Option[SchemaKey] =
        for {
          jsonSchema <- entity.asObject
          schemaUri <- jsonSchema.toMap.get("schema")
          schemaUriString <- schemaUri.asString
          schemaKey <- SchemaKey.fromUri(schemaUriString)
        } yield schemaKey

      def attachSchemaKey(schemaKey: SchemaKey, instance: Json): Json =
        Json.fromJsonObject(JsonObject.fromMap(
          Map(
            "schema" -> Json.fromString(schemaKey.toSchemaUri),
            "data" -> instance
          )
        ))

      def getContent(json: Json): Json = json
        .asObject
        .getOrElse(throw new RuntimeException("Iglu Core getContent: cannot parse into JSON object"))
        .apply("data")
        .getOrElse(throw new RuntimeException("Iglu Core getContent: cannot get `data`"))
    }

  final implicit val igluAttachToSchema: AttachSchemaMap[Json] with ToSchema[Json] with ExtractSchemaMap[Json] =
    new AttachSchemaMap[Json] with ToSchema[Json] with ExtractSchemaMap[Json] {

      def extractSchemaMap(entity: Json): Option[SchemaMap] =
        CirceIgluCodecs.parseSchemaMap(entity.hcursor) match {
          case Right(r) => Some(r)
          case Left(err) =>
            println(err)
            None
        }

      def attachSchemaMap(schemaMap: SchemaMap, schema: Json): Json = {
        val json = for {
          jsonSchema <- schema.asObject
          jsonObject = JsonObject.fromMap(
            Map("self" -> CirceIgluCodecs.encodeSchemaMap(schemaMap)) ++ jsonSchema.toMap
          )
        } yield Json.fromJsonObject(jsonObject)
        json.getOrElse(Json.Null)
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
}

object instances extends instances with CirceIgluCodecs
