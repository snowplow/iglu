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

/** Common error type for parsing core Iglu entities */
sealed trait ParseError extends Product with Serializable {
  def code: String
}

object ParseError {
  case object InvalidSchemaVer extends ParseError {
    def code = "INVALID_SCHEMAVER"
  }
  case object InvalidIgluUri extends ParseError {
    def code = "INVALID_IGLUURI"
  }
  case object InvalidData extends ParseError {
    def code = "INVALID_DATA_PAYLOAD"
  }
  case object InvalidSchema extends ParseError {
    def code = "INVALID_SCHEMA"
  }

  def parse(string: String): Option[ParseError] =
    List(InvalidSchemaVer, InvalidIgluUri, InvalidData, InvalidSchema).find { _.code == string }

  /** List parse function to get an entity that failed parsing */
  def liftParse[A, B](parser: A => Either[ParseError, B]): A => Either[(ParseError, A), B] =
    a => parser(a).left.map(e => (e, a))
}
