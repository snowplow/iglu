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
package jsonschema.json4s

// json4s
import org.json4s._

// This library
import jsonschema.NumberProperties._

object NumberSerializers {

  object MultipleOfSerializer extends CustomSerializer[MultipleOf](_ => (
    {
      case JInt(value)    => IntegerMultipleOf(value)
      case JDouble(value) => NumberMultipleOf(value)
      case x => throw new MappingException(x + " isn't numeric value")
    },

    {
      case NumberMultipleOf(value)  => JDouble(value.toDouble)
      case IntegerMultipleOf(value) => JInt(value)
    }
    ))

  object MaximumSerializer extends CustomSerializer[Maximum](_ => (
    {
      case JInt(value)    => IntegerMaximum(value)
      case JDouble(value) => NumberMaximum(value)
      case x => throw new MappingException(x + " isn't numeric value")
    },

    {
      case NumberMaximum(value) => JDouble(value.toDouble)
      case IntegerMaximum(value)   => JInt(value)
    }
    ))

  object MinimumSerializer extends CustomSerializer[Minimum](_ => (
    {
      case JInt(value)    => IntegerMinimum(value)
      case JDouble(value) => NumberMinimum(value)
      case x => throw new MappingException(x + " isn't numeric value")
    },

    {
      case NumberMinimum(value)  => JDouble(value.toDouble)
      case IntegerMinimum(value) => JInt(value)
    }
    ))
}
