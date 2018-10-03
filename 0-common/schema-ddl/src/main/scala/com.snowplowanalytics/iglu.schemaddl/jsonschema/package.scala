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

import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties

package object jsonschema {
  implicit class TypeMatcher(val jsonType: CommonProperties.Type) extends AnyVal {
    /** Check that type has exact type */
    def precisely(matchingType: CommonProperties.Type): Boolean =
      (jsonType, matchingType) match {
        case (CommonProperties.Type.Union(x), CommonProperties.Type.Union(y)) =>
          x.toSet == y.toSet
        case _ => jsonType == matchingType
      }

    /** Check if type can be `null` */
    def nullable: Boolean =
      jsonType match {
        case CommonProperties.Type.Union(union) => union.toSet.contains(CommonProperties.Type.Null)
        case CommonProperties.Type.Null => true
        case _ => false
      }

    /** Check if type is known type with `null` */
    def nullable(matchingType: CommonProperties.Type): Boolean =
      jsonType match {
        case CommonProperties.Type.Union(union) => union.toSet == Set(CommonProperties.Type.Null, matchingType)
        case _ => false
      }

    /** Check if type is known type, ignore `null` */
    def possiblyWithNull(matchingType: CommonProperties.Type): Boolean =
      precisely(matchingType) || nullable(matchingType)

    /** Check that type cannot be object or array */
    def isPrimitive: Boolean =
      jsonType match {
        case CommonProperties.Type.Union(types) =>
          val withoutNull = types.toSet - CommonProperties.Type.Null
          !withoutNull.contains(CommonProperties.Type.Array) && !withoutNull.contains(CommonProperties.Type.Object)
        case CommonProperties.Type.Object => false
        case CommonProperties.Type.Array => false
        case _ => true
      }
  }
}
