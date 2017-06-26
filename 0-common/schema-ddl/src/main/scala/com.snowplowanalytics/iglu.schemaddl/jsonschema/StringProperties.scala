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
package jsonschema

object StringProperties {

  /**
   * Marker trait for properties specific *ONLY* for strings
   */
  private[iglu] sealed trait StringProperty

  /**
   * Class representing `minLength` keyword
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor29
   */
  case class MinLength(value: BigInt) extends JsonSchemaProperty with StringProperty {
    def keyName = "minLength"
  }

  /**
   * Class representing `maxLength` keyword
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor26
   */
  case class MaxLength(value: BigInt) extends JsonSchemaProperty with StringProperty {
    def keyName = "maxLength"
  }

  /**
   * ADT representing all possible values for `format` keyword
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor104
   */
  sealed trait Format extends JsonSchemaProperty with StringProperty {
    def keyName = "format"
    def asString: String
  }
  case object UriFormat extends Format { val asString = "uri" }
  case object Ipv4Format extends Format { val asString = "ipv4" }
  case object Ipv6Format extends Format { val asString = "ipv6" }
  case object EmailFormat extends Format { val asString = "email" }
  case object DateTimeFormat extends Format { val asString = "date-time" }
  case object DateFormat extends Format { val asString = "date" }
  case object HostNameFormat extends Format { val asString = "hostname" }

  /**
   * Implementations MAY add custom format attributes
   */
  case class CustomFormat(value: String) extends Format with StringProperty  { val asString = value }

  /**
   * Class representing `pattern` keyword
   *
   * @see http://json-schema.org/latest/json-schema-validation.html#anchor33
   */
  case class Pattern(value: String) extends JsonSchemaProperty with StringProperty {
    def keyName = "pattern"
  }
}


