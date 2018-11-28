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

// json4s
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.compact

// This library
import com.snowplowanalytics.iglu.core.typeclasses._

/**
 * This module contains examples of common traits and type class instances
 * based on Json4s library
 */
object IgluCoreCommon {

  implicit val formats = IgluJson4sCodecs.formats

  ////////////////////////
  // ExtractFrom Json4s //
  ////////////////////////

  /**
   * Example common trait for [[ExtractSchemaKey]] *data* objects
   * It can also be instantiated or extended by [[AttachSchemaKey]]
   */
  trait Json4SExtractSchemaKeyData extends ExtractSchemaKey[JValue] {
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      entity \ "schema" match {
        case JString(schema) => SchemaKey.fromUri(schema)
        case _               => None
      }
  }

  /**
   * Example of [[ExtractSchemaKey]] instance for json4s JSON *data*
   */
  implicit object Json4SExtractSchemaKeyData extends Json4SExtractSchemaKeyData

  /**
   * Example common trait for [[ExtractSchemaKey]] *Schemas* objects
   * It can also be instantiated or extended by [[AttachSchemaKey]]
   */
  trait Json4SExtractSchemaKeySchema extends ExtractSchemaKey[JValue] {
    /**
     * Extract SchemaKey usning serialization formats defined at [[IgluJson4sCodecs]]
     */
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "self").extractOpt[SchemaKey]
  }

  /**
   * Example of [[ExtractSchemaKey]] instance for json4s JSON *Schemas*
   */
  implicit object Json4SExtractSchemaKeySchema extends Json4SExtractSchemaKeySchema


  /////////////////////
  // AttachTo Json4s //
  /////////////////////

  // Schemas

  implicit object Json4SAttachSchemaKeySchema extends ExtractSchemaKey[JValue] {
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "self").extractOpt[SchemaKey]
  }

  implicit object Json4SAttachSchemaMapComplex extends ExtractSchemaMap[JValue] with ToSchema[JValue] {
    def extractSchemaMap(entity: JValue): Option[SchemaMap] = {
      implicit val formats = IgluJson4sCodecs.formats
      (entity \ "self").extractOpt[SchemaKey].map(key => SchemaMap(key))
    }

    /**
     * Remove key with `self` description
     * `getContent` required to be implemented here because it extends [[ToSchema]]
     */
    def getContent(json: JValue): JValue =
      removeSelf(json) match {
        case JNothing => JNothing
        case content => content
      }
  }

  // Data

  implicit object Json4SAttachSchemaKeyData extends ExtractSchemaKey[JValue] with ToData[JValue] with Json4SExtractSchemaKeyData {

    def getContent(json: JValue): JValue =
      json \ "data" match {
        case JNothing => JNothing
        case data => data
      }

    def attachSchemaKey(schemaKey: SchemaKey, instance: JValue): JValue =
      ("schema" -> schemaKey.toSchemaUri) ~ ("data" -> instance)
  }

  //////////////////////////
  // ExtractFrom non-JSON //
  //////////////////////////

  /**
   * Stub class bearing its Schema
   */
  case class DescribedString(
    vendor: String,
    name: String,
    format: String,
    model: Int,
    revision: Int,
    addition: Int,
    data: String)

  /**
   * Example of [[ExtractSchemaKey]] instance for usual case class
   */
  implicit object DescribingStringInstance extends ExtractSchemaKey[DescribedString] {
    def extractSchemaKey(entity: DescribedString): Option[SchemaKey] =
      Some(
        SchemaKey(
          entity.vendor,
          entity.name,
          entity.format,
          SchemaVer.Full(entity.model, entity.revision, entity.addition)
        )
      )
  }

  ///////////////
  // Normalize //
  ///////////////

  implicit object Json4SNormalizeSchema extends NormalizeSchema[JValue] {
    def normalize(schema: SelfDescribingSchema[JValue]): JValue =
      Extraction.decompose(schema)
  }

  implicit object Json4SNormalizeData extends NormalizeData[JValue] {
    def normalize(instance: SelfDescribingData[JValue]): JValue =
      Extraction.decompose(instance)
  }

  object StringifySchema extends StringifySchema[JValue] {
    def asString(container: SelfDescribingSchema[JValue]): String =
      compact(container.normalize(Json4SNormalizeSchema))
  }

  object StringifyData extends StringifyData[JValue] {
    def asString(container: SelfDescribingData[JValue]): String =
      compact(container.normalize(Json4SNormalizeData))
  }

  /////////
  // Aux //
  /////////

  def removeSelf(json: JValue): JValue = json match {
    case JObject(fields) =>
      fields.filterNot {
        case ("self", JObject(keys)) => intersectsWithSchemakey(keys)
        case _ => false
      }
    case jvalue => jvalue
  }

  private def intersectsWithSchemakey(fields: List[JField]): Boolean =
    fields.map(_._1).toSet.diff(Set("name", "vendor", "format", "version")).isEmpty
}
