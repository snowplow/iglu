/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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

import jsonschema.properties.CommonProperties.Type
import jsonschema.properties.StringProperty.Format


package object jsonschema {

  implicit class TypeMatcher(val jsonType: Type) extends AnyVal {
    /** Check that type has exact type */
    def precisely(matchingType: Type): Boolean =
      (jsonType, matchingType) match {
        case (Type.Union(x), Type.Union(y)) =>
          x.toSet == y.toSet
        case _ => jsonType == matchingType
      }

    /** Check if type can be `null` */
    def nullable: Boolean =
      jsonType match {
        case Type.Union(union) => union.toSet.contains(Type.Null)
        case Type.Null => true
        case _ => false
      }

    /** Check if type is known type with `null`, precisely [X, null] */
    def nullable(matchingType: Type): Boolean =
      jsonType match {
        case Type.Union(union) => union.toSet == Set(Type.Null, matchingType)
        case _ => false
      }

    /** Check if type is known type, [X, null] OR X */
    def possiblyWithNull(matchingType: Type): Boolean =
      precisely(matchingType) || nullable(matchingType)

    /** Check that type cannot be object or array */
    def isPrimitive: Boolean =
      jsonType match {
        case Type.Union(types) =>
          val withoutNull = types.toSet - Type.Null
          !withoutNull.contains(Type.Array) && !withoutNull.contains(Type.Object)
        case Type.Object => false
        case Type.Array => false
        case _ => true
      }

    /** Check if type contains at least two non-null types */
    def isUnion: Boolean =
      jsonType match {
        case Type.Union(types) =>
          (types.toSet - Type.Null).size > 1
        case _ => false
      }
  }

  /** Pimp JSON Schema AST with method checking presence of some JSON type */
  private[schemaddl] implicit class SchemaOps(val value: Schema) extends AnyVal {
    /** Check if Schema has no specific type *OR* has no type at all */
    def withoutType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Type.Union(types)) => !types.contains(jsonType)
        case Some(t) => t != jsonType
        case None => false            // absent type is ok
      }

    /** Check if Schema has no specific type *OR* has no type at all */
    def withType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Type.Union(types)) => types.contains(jsonType)
        case Some(t) => t == jsonType
        case None => false            // absent type is ok
      }

    /** Check if Schema has specified format */
    def withFormat(format: Format): Boolean =
      value.format match {
        case Some(f) => format == f
        case None => false
      }
  }
}
