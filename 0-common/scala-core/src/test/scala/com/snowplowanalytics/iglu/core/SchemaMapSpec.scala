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

// specs2
import org.specs2.Specification

class SchemaMapSpec extends Specification { def is = s2"""
  Specification for parsing SchemaKey
    parse simple correct schema map from Iglu path $e1
  """

  def e1 = {
    val path = "uk.edu.acme.sub-division/second-event_complex/jsonschema/2-10-32"
    SchemaMap.fromPath(path) must beRight(
      SchemaMap("uk.edu.acme.sub-division", "second-event_complex", "jsonschema", SchemaVer.Full(2, 10, 32)))
  }
}
