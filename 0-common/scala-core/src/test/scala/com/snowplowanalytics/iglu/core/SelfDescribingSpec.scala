/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu
package core

// specs2
import org.specs2.Specification

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// This library
import SelfDescribed._
import IgluCoreCommon._

class SelfDescribingSpec extends Specification { def is = s2"""
  Specification SelfDescribing type class
    extract SchemaKey using postfix method $e1
  """

  def e1 = {
    val json: JValue = parse(
      """
        |{
        |  "schema": "iglu:com.acme.useless/null/jsonschema/2-0-3",
        |  "data": null
        |}
      """.stripMargin)

    json.getSchemaKey must beSome(
      SchemaKey("com.acme.useless", "null", "jsonschema", SchemaVer(2,0,3))
    )
  }
}

