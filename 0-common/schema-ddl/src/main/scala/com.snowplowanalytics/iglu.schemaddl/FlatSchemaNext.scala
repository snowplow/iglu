/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.schemaddl

import jsonschema.Schema
import jsonschema.CommonProperties._

/**
 * Module supposed to supersede old flattening algorithm with stringified
 * subschemas when Schema AST will be incorporated into other parts of Iglu
 */
object FlatSchemaNext {

  /**
   * Path of property represented as list of keys, e.g:
   * `$.data.context` is List("data", "context")
   */
  type Path = List[String]

  /**
   * List of recursively extracted Schema properties
   * Where each (deep) path is key and subschema is Schema
   */
  type FlatProperties = List[(Path, Schema)]

  /**
   * Flatten Schema into list of paths with properties
   * Primary function of a module
   *
   * @param schema JSON Schema
   * @return some list of paths with properties if Schema has `properties` key
   *         none otherwise
   */
  def flattenSchema(schema: Schema): Option[FlatProperties] =
    getProperties(schema).map(traverseProperties(_, Nil))

  /**
   * Extract `properties` key as [[FlatProperties]] list
   *
   * @param schema JSON Schema (with properties or not) to extract
   *               `properties` from
   * @return some list of properties if Schema has `properties`, none otherwise
   */
  def getProperties(schema: Schema): Option[FlatProperties] =
    schema.properties.map(_.value).map { properties =>
      properties.toList.map { case (k, v) => (List(k), v) }
    }

  /**
   * Traverse flatten properties to find out properties which can be
   * disassembled (objects) and recursively traverse them, then join .
   *
   * @param flatSchema list of flatten properties, which can have both
   *                   primitive (already flat) subschemas and ready to be
   *                   disassembled (objects)
   * @param initPath accumulated path of parent subschemas
   * @return all flat properties
   */
  def traverseProperties(flatSchema: FlatProperties, initPath: List[String] = Nil): FlatProperties = {
    val (primitives, complex) = splitPrimitives(flatSchema, initPath)

    // traverse only into complex objects
    val result = complex.foldLeft(Nil: FlatProperties) { case (acc, (path, schema)) =>
      getProperties(schema) match {
        case Some(properties) => traverseProperties(properties, path) ++ acc
        // None will be returned for non-object with `properties`
        case None             => List((initPath ++ path, schema))
      }
    }

    primitives ++ result
  }

  /**
   * Split list of properties into already flat (primitives like strings,
   * nulls) and still complex (objects)
   *
   * @param properties list of mixed properties
   * @param initPath accumulated path of current subschema
   * @return pair of flat and complext lists of subschemas
   */
  private def splitPrimitives(properties: FlatProperties, initPath: List[String]): (FlatProperties, FlatProperties) = {
    val (c, p) = properties.partition { field => isObject(field._2) }

    val primitives: FlatProperties =
      p.map { case (key, schema) => (initPath ++ key, schema) }

    val complex: FlatProperties =
      c.map { case (key, schema) => (initPath ++ key, schema) }

    (primitives, complex)
  }

  /**
   * Check if JSON Schema contains `object` as a type
   *
   * @param schema JSON Schema
   * @return true if Schema is pure object or coproduct of object
   */
  private def isObject(schema: Schema): Boolean =
    schema.`type` match {
      case Some(Product(types)) => types.contains(Object)
      case Some(Object) => true
      case _ => false
    }
}
