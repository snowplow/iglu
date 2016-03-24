/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
import Containers._

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
   * Example common trait for [[ExtractFrom]] *data* objects
   * It can also be instantiated or extended by [[AttachTo]]
   */
  trait Json4sExtractFromData extends ExtractFrom[JValue] {
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      entity \ "schema" match {
        case JString(schema) => SchemaKey.fromUri(schema)
        case _               => None
      }
  }

  /**
   * Example of [[ExtractFrom]] instance for json4s JSON *data*
   */
  implicit object Json4sExtractFromData extends Json4sExtractFromData

  /**
   * Example common trait for [[ExtractFrom]] *Schemas* objects
   * It can also be instantiated or extended by [[AttachTo]]
   */
  trait Json4sExtractFromSchema extends ExtractFrom[JValue] {
    /**
     * Extract SchemaKey usning serialization formats defined at [[IgluJson4sCodecs]]
     */
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "self").extractOpt[SchemaKey]
  }

  /**
   * Example of [[ExtractFrom]] instance for json4s JSON *Schemas*
   */
  implicit object Json4sExtractFromSchema extends Json4sExtractFromSchema


  /////////////////////
  // AttachTo Json4s //
  /////////////////////

  // Schemas

  /**
   * Example of simple [[AttachTo]] JSON *Schema* for json4s
   */
  implicit object Json4sAttachToSchema extends AttachTo[JValue] {
    def attachSchemaKey(schemaKey: SchemaKey, schema: JValue): JValue =
      (("self", Extraction.decompose(schemaKey)): JObject).merge(schema)

    /**
     * Extract SchemaKey using serialization formats defined at [[IgluJson4sCodecs]]
     * `extractSchemaKey` is also required to be implemented in [[AttachTo]] because it
     * is supreme type class
     */
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "self").extractOpt[SchemaKey]
  }

  /**
   * Example of full-featured [[AttachTo]] JSON *Schema* for json4s
   * Unlike almost identical [[Json4sAttachToSchema]] it also extends
   * [[ToSchema]] which makes possible to use `toSchema` method
   */
  implicit object Json4sAttachToSchemaComplex extends AttachTo[JValue] with ToSchema[JValue] {
    def attachSchemaKey(schemaKey: SchemaKey, schema: JValue): JValue = {
      (("self", Extraction.decompose(schemaKey)): JObject).merge(schema)
    }

    /**
     * Extract SchemaKey using serialization formats defined at [[IgluJson4sCodecs]]
     * `extractSchemaKey` is also required to be implemented in [[AttachTo]] because it
     * is supreme type class
     */
    def extractSchemaKey(entity: JValue): Option[SchemaKey] =
      (entity \ "self").extractOpt[SchemaKey]

    /**
     * Remove key with `self` description
     * `getContent` required to be implemented here because it extends [[ToSchema]]
     */
    def getContent(json: JValue): Option[JValue] =
      removeSelf(json) match {
        case JNothing => None
        case content  => Some(content)
      }
  }

  // Data

  /**
   * Example of full-featured [[AttachTo]] JSON *instance* for json4s
   * Contains all available mixins
   */
  implicit object Json4sAttachToData extends AttachTo[JValue] with ToData[JValue] with Json4sExtractFromData {

    def getContent(json: JValue): Option[JValue] =
      json \ "data" match {
        case JNothing     => None
        case data: JValue => Some(data)
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
   * Example of [[ExtractFrom]] instance for usual case class
   */
  implicit object DescribingStringInstance extends ExtractFrom[DescribedString] {
    def extractSchemaKey(entity: DescribedString): Option[SchemaKey] =
      Some(
        SchemaKey(
          entity.vendor,
          entity.name,
          entity.format,
          SchemaVer(entity.model, entity.revision, entity.addition)
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
