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

import typeclasses.ExtractSchemaMap

/**
  * Entity describing a schema object itself, duality of `SchemaKey`
  * **Should be used only with schemas**
  */
final case class SchemaMap(schemaKey: SchemaKey) extends AnyVal

object SchemaMap {

  /** Regular expression to extract SchemaKey from path */
  val schemaPathRegex = (
    "^([a-zA-Z0-9-_.]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([1-9][0-9]*" +
      "(?:-(?:0|[1-9][0-9]*)){2})$").r

  /** Regex to extract SchemaVer separately */
  private val schemaPathRigidRegex = (
    "^([a-zA-Z0-9-_.]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([a-zA-Z0-9-_]+)/" +
      "([0-9]*(?:-(?:[0-9]*)){2})$").r

  def apply(vendor: String, name: String, format: String, version: SchemaVer.Full): SchemaMap =
    SchemaMap(SchemaKey(vendor, name, format, version))

  /**
    * Custom constructor for an Iglu SchemaMap from
    * an Iglu-format Schema path, which looks like:
    * com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0
    * Can be used get Schema key by path on file system
    * Exists only in `SchemaMap` because schemas are stored at certain paths
    *
    * @param schemaPath an Iglu-format Schema path
    * @return some SchemaMap for Success, and none for failure
    */
  def fromPath(schemaPath: String): Either[ParseError, SchemaMap] = schemaPath match {
    case schemaPathRigidRegex(vnd, n, f, ver) =>
      SchemaVer.parse(ver) match {
        case Right(v: SchemaVer.Full) => Right(SchemaMap(vnd, n, f, v))
        case Right(_: SchemaVer.Partial) => Left(ParseError.InvalidSchemaVer)
        case Left(other) => Left(other)
      }
    case _ => Left(ParseError.InvalidSchema)
  }

  /** Try to get `SchemaMap` from `E` as */
  def extract[E: ExtractSchemaMap](e: E): Either[ParseError, SchemaMap] =
    implicitly[ExtractSchemaMap[E]].extractSchemaMap(e)
}
