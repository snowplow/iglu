/*
 * Copyright (c) 2012-2017 Snowplow Analytics Ltd. All rights reserved.
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
package json4s

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.compact

import com.snowplowanalytics.iglu.core.typeclasses._

trait instances {

  private implicit val codecs = Json4sIgluCodecs.formats

  final implicit val igluAttachToDataJValue: ExtractSchemaKey[JValue] with ToData[JValue] =
    new ExtractSchemaKey[JValue] with ToData[JValue] {

      def extractSchemaKey(entity: JValue) =
        entity \ "schema" match {
          case JString(schema) => SchemaKey.fromUri(schema)
          case _               => Left(ParseError.InvalidData)
        }

      def getContent(json: JValue): Either[ParseError, JValue] =
        json \ "data" match {
          case JNothing => Left(ParseError.InvalidData)
          case data => Right(data)
        }
    }

  final implicit val igluAttachToSchema: ExtractSchemaMap[JValue] with ToSchema[JValue] with ExtractSchemaMap[JValue] =
    new ToSchema[JValue] with ExtractSchemaMap[JValue] {

      def extractSchemaMap(entity: JValue): Either[ParseError, SchemaMap] =
        (entity \ "self").extractOpt[SchemaKey].map(key => SchemaMap(key)) match {
          case Some(map) => Right(map)
          case None => Left(ParseError.InvalidSchema)
        }

      def getContent(schema: JValue): JValue = schema match {
        case JObject(fields) =>
          fields.filterNot {
            case ("self", JObject(keys)) => intersectsWithSchemakey(keys)
            case _ => false
          }
        case json => json
      }

      private[this] def intersectsWithSchemakey(fields: List[JField]): Boolean =
        fields.map(_._1).toSet.diff(Set("name", "vendor", "format", "version")).isEmpty

    }

  // Container-specific instances

  final implicit val igluNormalizeDataJValue: NormalizeData[JValue] =
    new NormalizeData[JValue] {
      def normalize(instance: SelfDescribingData[JValue]): JValue =
        Extraction.decompose(instance)
    }

  final implicit val igluNormalizeSchemaJValue: NormalizeSchema[JValue] =
    new NormalizeSchema[JValue] {
      def normalize(schema: SelfDescribingSchema[JValue]): JValue =
        Extraction.decompose(schema)
    }

  final implicit val igluStringifyDataJValue: StringifyData[JValue] =
    new StringifyData[JValue] {
      def asString(container: SelfDescribingData[JValue]): String =
        compact(container.normalize(igluNormalizeDataJValue))
    }

  final implicit val igluStringifySchemaJValue: StringifySchema[JValue] =
    new StringifySchema[JValue] {
      def asString(container: SelfDescribingSchema[JValue]): String =
        compact(container.normalize(igluNormalizeSchemaJValue))
    }
}

object instances extends instances
