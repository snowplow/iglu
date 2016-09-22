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

// Scalaz
import scalaz._
import Scalaz._

// Iglu
import com.snowplowanalytics.iglu.core.SchemaKey
import com.snowplowanalytics.iglu.schemaddl.{ IgluSchema, RevisionGroup, ModelGroup }

// This library
import FileUtils.{ JsonFile, splitPath }

object Utils {

  type Failing[+A] = String \/ A

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
   * @param schemaKey schema key extracted from it
   * @return true if extracted path is equal to FS path
   */
  def equalPath(jsonFile: JsonFile, schemaKey: SchemaKey): Boolean = {
    val path = getPath(jsonFile.origin.getAbsolutePath)
    SchemaKey.fromPath(path).contains(schemaKey)
  }

  /**
   * Extract self-describing JSON Schema from JSON file
   *
   * @param jsonFile some existing on FS valid JSON file
   * @return self-describing JSON Schema if successful or error message if
   *         file is not Schema or self-describing Schema or has invalid
   *         file path
   */
  def extractSchema(jsonFile: JsonFile): String \/ IgluSchema =
    jsonFile.extractSelfDescribingSchema.disjunction match {
      case \/-(schema) if equalPath(jsonFile, schema.self) => schema.right
      case \/-(schema) => s"Error: JSON Schema [${schema.self.toSchemaUri}] doesn't conform path [${getPath(jsonFile.getKnownPath)}]".left
      case -\/(error) => error.left
    }

  /**
   * Split list of scalaz Validation in pair of List with successful values
   * and List with unsuccessful values
   *
   * @param validations probably empty list of Scalaz Validations
   * @tparam F failure type
   * @tparam S success type
   * @return tuple with list of failure and list of successes
   */
  def splitValidations[F, S](validations: List[Validation[F, S]]): (List[F], List[S]) =
    validations.foldLeft((List.empty[F], List.empty[S]))(splitValidation)

  /**
   * Helper function for [[splitValidations]]
   */
  private def splitValidation[F, S](acc: (List[F], List[S]), current: Validation[F, S]): (List[F], List[S]) =
    current match {
      case Success(json) => (acc._1, json :: acc._2)
      case Failure(fail) => (fail :: acc._1, acc._2)
    }

  /**
   * Extract from Schema description four elements defining REVISION
   *
   * @param schemaKey Schema description
   * @return tuple of four values defining revision
   */
  private[iglu] def revisionGroup(schemaKey: SchemaKey): RevisionGroup =
    (schemaKey.vendor, schemaKey.name, schemaKey.version.model, schemaKey.version.revision)

  /**
   * Extract from Schema description three elements defining MODEL
   *
   * @param schemaKey Schema description
   * @return tuple of three values defining revision
   */
  private[iglu] def modelGroup(schemaKey: SchemaKey): ModelGroup =
    (schemaKey.vendor, schemaKey.name, schemaKey.version.model)

  /**
   * Convert disjunction value into `EitherT`
   * Used in for-comprehensions to mimic disjunction as `Stream[String \/ A]`
   * and extract A
   */
  private[iglu] def fromXor[A](value: Failing[A]): EitherT[Stream, String, A] =
    EitherT[Stream, String, A](value.point[Stream])

  /**
   * Convert stream of disjunctions into `EitherT`
   * Used in for-comprehensions to mimic disjunction as `Stream[String \/ A]`
   * and extract A
   */
  private[iglu] def fromXors[A, B](value: Stream[Failing[A]]): EitherT[Stream, String, A] =
    EitherT[Stream, String, A](value)
}

