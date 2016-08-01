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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

// json4s
import org.json4s._

// So far only `enum` need to be parametrized with JSON AST type
// and `Type` need to know about representation (to render product type)
object CommonProperties {

  /**
   * AST representing value for `type` key in JSON Schema
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor79
   */
  sealed trait Type extends JsonSchemaProperty {
    def keyName = "type"
    def asJson: JValue
  }

  case object Null extends Type {
    def asJson = JString("null")
  }

  case object Boolean extends Type {
    def asJson = JString("boolean")
  }

  case object String extends Type {
    def asJson = JString("string")
  }

  case object Integer extends Type {
    def asJson = JString("integer")
  }

  case object Number extends Type {
    def asJson = JString("number")
  }

  case object Array extends Type {
    def asJson = JString("array")
  }

  case object Object extends Type {
    def asJson = JString("object")
  }

  case class Product(value: List[Type]) extends Type {
    def asJson = JArray(value.map(_.asJson))

    def hasNull: Boolean = value.contains(Null)
  }


  /**
   * Type representing value for `enum` key
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor76
   */
  case class Enum(value: List[JValue]) extends JsonSchemaProperty { def keyName = "enum" }


  /**
   * Type representing value for `oneOf` key
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor88
   */
  case class OneOf(value: List[Schema]) extends JsonSchemaProperty { def keyName = "oneOf" }

}



