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
package com.snowplowanalytics.iglu.ctl
package commands

// java
import java.nio.file.Paths

import com.snowplowanalytics.iglu.core.SchemaVer

// json4s
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

// This project
import com.snowplowanalytics.iglu.core.SchemaMap
import com.snowplowanalytics.iglu.ctl.File.jsonFile
import com.snowplowanalytics.iglu.ctl.Common.Error


class PushSpec extends Specification { def is = s2"""
  Registry sync command (sync) specification
    check paths on FS and SchemaKey.toPath correspondence $e1
  """

  def e1 = {
    // valid
    val schema1 = parse(
      """
        |{
        |  "self": {
        |    "vendor": "com.acme",
        |    "name": "event",
        |    "format": "jsonschema",
        |    "version": "1-0-2"
        |  },
        |  "type": "object"
        |}
      """.stripMargin)
    val jsonFile1 = jsonFile(Paths.get("/path/to/schemas/com.acme/event/jsonschema/1-0-2"), schema1)

    ClassLoader.getSystemClassLoader.getResource("")

    // invalid SchemaVer
    val schema2 =  parse(
      """
        |{
        |  "self": {
        |    "vendor": "com.acme",
        |    "name": "event",
        |    "format": "jsonschema",
        |    "version": "1-0-1"
        |  },
        |  "type": "object"
        |}
      """.stripMargin)
    val jsonFile2 = jsonFile(Paths.get("/path/to/schemas/com.acme/event/jsonschema/1-0-2"), schema2)

    // not self-describing
    val schema3 =  parse(
      """
        |{
        |  "type": "object"
        |}
      """.stripMargin)
    val jsonFile3 = jsonFile(Paths.get("/path/to/schemas/com.acme/event/jsonschema/1-0-2"), schema3)

    // not full path
    val schema4 = parse(
      """
        |{
        |  "self": {
        |    "vendor": "com.acme",
        |    "name": "event",
        |    "format": "jsonschema",
        |    "version": "1-0-2"
        |  },
        |  "type": "object"
        |}
      """.stripMargin)
    val jsonFile4 = jsonFile(Paths.get("/event/jsonschema/1-0-2"), schema4)

    val validSchemaExpectation = jsonFile1.asSchema must beRight
    val mismatchedSchemaVerExpectation = jsonFile2.asSchema must beLeft(Error.PathMismatch(Paths.get("/path/to/schemas/com.acme/event/jsonschema/1-0-2"), SchemaMap("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,1))))
    val invalidSchemaExpectation = jsonFile3.asSchema must  beLeft(Error.ParseError(Paths.get("/path/to/schemas/com.acme/event/jsonschema/1-0-2"), "Cannot extract Self-describing JSON Schema from JSON file"))
    val invalidShortPathExpectation = jsonFile4.asSchema must beLeft(Error.PathMismatch(Paths.get("/event/jsonschema/1-0-2"),SchemaMap("com.acme","event","jsonschema",SchemaVer.Full(1,0,2))))

    validSchemaExpectation and mismatchedSchemaVerExpectation and invalidSchemaExpectation and invalidShortPathExpectation
  }
}
