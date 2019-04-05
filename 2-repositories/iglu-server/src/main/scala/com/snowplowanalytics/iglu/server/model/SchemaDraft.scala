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
package model

import doobie._
import doobie.postgres.circe.json.implicits._

import io.circe.{ Json, Encoder }
import io.circe.generic.semiauto.deriveEncoder
import io.circe.refined._

import eu.timepit.refined.types.numeric.NonNegInt

import com.snowplowanalytics.iglu.server.model.SchemaDraft.DraftId
import storage.Storage.IncompatibleStorage

import Schema.Metadata

case class SchemaDraft(schemaMap: DraftId, metadata: Metadata, body: Json)

object SchemaDraft {
  case class DraftId(vendor: String, name: String, format: String, version: DraftVersion)

  implicit def draftIdEncoder: Encoder[DraftId] =
    deriveEncoder[DraftId]

  implicit def draftEncoder: Encoder[SchemaDraft] =
    deriveEncoder[SchemaDraft]

  implicit val draftVersionMeta: Meta[DraftVersion] =
    Meta[Int].timap(int => NonNegInt.from(int).fold(x => throw IncompatibleStorage(x), identity))(x => x.value)

  implicit val schemaDraftDoobieRead: Read[SchemaDraft] =
    Read[(DraftId, Metadata, Json)].map {
      case (id, meta, body) =>
        SchemaDraft(id, meta, body)
    }
}
