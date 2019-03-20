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
package properties

/**
  * Marker trait for properties specific *ONLY* for numbers and integers
  */
private[iglu] sealed trait NumberProperty

object NumberProperty {

  /**
   * AST representing keyword `multipleOf`
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor14
   */
  sealed trait MultipleOf extends JsonSchemaProperty with NumberProperty {
    val keyword: Keyword = Keyword.MultipleOf
  }
  object MultipleOf {
    case class NumberMultipleOf(value: BigDecimal) extends MultipleOf
    case class IntegerMultipleOf(value: BigInt) extends MultipleOf
  }

  /**
   * AST representing keyword `minimum`
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor17
   */
  sealed trait Minimum extends JsonSchemaProperty with NumberProperty {
    val keyword: Keyword = Keyword.Minimum

    /**
     * Get value of `minimum` property as `BigDecimal` preserving point for
     * `NumberMinimum` and adding it for `IntegerMinimum`
     */
    def getAsDecimal: BigDecimal
  }
  object Minimum {
    case class NumberMinimum(value: BigDecimal) extends Minimum {
      def getAsDecimal = value
    }
    case class IntegerMinimum(value: BigInt) extends Minimum {
      def getAsDecimal = BigDecimal(value)
    }
  }

  /**
   * AST representing keyword `maximum`
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor17
   */
  sealed trait Maximum extends JsonSchemaProperty with NumberProperty {
    val keyword: Keyword = Keyword.Maximum

    /**
     * Get value of `maximum` property as `BigDecimal` preserving point for
     * `NumberMaximum` and adding it for `IntegerMaximum`
     */
    def getAsDecimal: BigDecimal
  }
  object Maximum {
    case class NumberMaximum(value: BigDecimal) extends Maximum {
      def getAsDecimal = value
    }
    case class IntegerMaximum(value: BigInt) extends Maximum {
      def getAsDecimal = BigDecimal(value)
    }
  }
}


