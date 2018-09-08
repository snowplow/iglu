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
package com.snowplowanalytics.iglu.ctl

// cats
import cats.syntax.either._

// Json4s
import org.json4s.{ JValue, MappingException, Formats }
import org.json4s.jackson.JsonMethods.compact

// Iglu
import com.snowplowanalytics.iglu.core.SchemaMap
import com.snowplowanalytics.iglu.schemaddl.{ IgluSchema, RevisionGroup, ModelGroup }

// This library
import FileUtils.{ JsonFile, splitPath }

object Utils {

  type Failing[+A] = Either[String, A]

  /**
   * Get Iglu-compatible path (com.acme/event/jsonschema/1-0-2) from full
   * absolute file path
   *
   * @param fullPath file's absolute path
   * @return four last path entities joined by OS-separator
   */
  def getPath(fullPath: String): String =
    splitPath(fullPath).takeRight(4).mkString("/")  // Always URL-compatible

  /**
   * Check if path of some JSON file corresponds with Iglu path extracted
   * from its self-describing info
   *
   * @param jsonFile some existing JSON file with defined path in it
   * @param schemaMap schema key extracted from it
   * @return true if extracted path is equal to FS path
   */
  def equalPath(jsonFile: JsonFile, schemaMap: SchemaMap): Boolean = {
    val path = getPath(jsonFile.origin.getAbsolutePath)
    SchemaMap.fromPath(path).toOption.contains(schemaMap)
  }

  /**
   * Extract self-describing JSON Schema from JSON file
   *
   * @param jsonFile some existing on FS valid JSON file
   * @return self-describing JSON Schema if successful or error message if
   *         file is not Schema or self-describing Schema or has invalid
   *         file path
   */
  def extractSchema(jsonFile: JsonFile): Either[String, IgluSchema] =
    jsonFile.extractSelfDescribingSchema match {
      case Right(schema) if equalPath(jsonFile, schema.self) => schema.asRight
      case Right(schema) => s"Error: JSON Schema [${schema.self.schemaKey.toSchemaUri}] doesn't conform path [${getPath(jsonFile.getKnownPath)}]".asLeft
      case Left(error) => error.asLeft
    }

  /** Json4s method for extracting deserialized data */
  def extractKey[A](json: JValue, key: String)(implicit ev: Manifest[A], formats: Formats): Either[String, A] =
    try {
      Right((json \ key).extract[A])
    } catch {
      case _: MappingException => Left(s"Cannot extract key $key from ${compact(json)}")
    }


  /**
   * Extract from Schema description four elements defining REVISION
   *
   * @param schemaMap Schema description
   * @return tuple of four values defining revision
   */
  private[iglu] def revisionGroup(schemaMap: SchemaMap): RevisionGroup =
    (schemaMap.schemaKey.vendor,
      schemaMap.schemaKey.name,
      schemaMap.schemaKey.version.model,
      schemaMap.schemaKey.version.revision)

  /**
   * Extract from Schema description three elements defining MODEL
   *
   * @param schemaMap Schema description
   * @return tuple of three values defining revision
   */
  private[iglu] def modelGroup(schemaMap: SchemaMap): ModelGroup =
    (schemaMap.schemaKey.vendor, schemaMap.schemaKey.name, schemaMap.schemaKey.version.model)
}

