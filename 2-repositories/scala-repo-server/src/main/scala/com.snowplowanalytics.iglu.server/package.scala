/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu

// Json schema
import com.github.fge.jsonschema.core.report.ProcessingMessage

// Scala
import scala.util.matching.Regex

// Scalaz
import scalaz._

package object server {
  type ValidatedNel[A] = ValidationNel[ProcessingMessage, A]

  val VendorPattern: Regex = "[a-zA-Z0-9-_.]+".r
  val NamePattern: Regex = "[a-zA-Z0-9-_]+".r
  val FormatPattern: Regex = "[a-zA-Z0-9-_]+".r
  val VersionPattern: Regex = "([1-9][0-9]*(?:-(?:0|[1-9][0-9]*)){2})".r
}
