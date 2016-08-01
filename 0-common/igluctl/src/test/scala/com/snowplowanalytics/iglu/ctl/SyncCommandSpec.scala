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

// java
import java.io.File

// json4s
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

// This project
import FileUtils.JsonFile

// scalaz
import scalaz._
import Scalaz._

class SyncCommandSpec extends Specification { def is = s2"""
  Registry sync command (sync) specification
    check paths on FS and SchemaKey.toPath correspondence $e1
    short-circuit request-generation on invalid apikey $e2
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
    val jsonFile1 = JsonFile(Some("/path/to/schemas/com.acme/event/jsonschema"), "1-0-2", schema1)

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
    val jsonFile2 = JsonFile(Some("/path/to/schemas/com.acme/event/jsonschema"), "1-0-2", schema2)

    // not self-describing
    val schema3 =  parse(
      """
        |{
        |  "type": "object"
        |}
      """.stripMargin)
    val jsonFile3 = JsonFile(Some("/path/to/schemas/com.acme/event/jsonschema"), "1-0-2", schema3)

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
    val jsonFile4 = JsonFile(Some("/event/jsonschema"), "1-0-2", schema4)

    (LintCommand.extractSchema(jsonFile1).toEither must beRight).and(
      LintCommand.extractSchema(jsonFile2).toEither must beLeft.like {
        case error => error must beEqualTo("Error: JSON Schema [iglu:com.acme/event/jsonschema/1-0-1] doesn't conform path [com.acme/event/jsonschema/1-0-2]")
      }
    ).and(
      LintCommand.extractSchema(jsonFile3).toEither must beLeft("Cannot extract Self-describing JSON Schema from JSON file [/path/to/schemas/com.acme/event/jsonschema/1-0-2]")
    ).and(
      LintCommand.extractSchema(jsonFile4).toEither must beLeft("Error: JSON Schema [iglu:com.acme/event/jsonschema/1-0-2] doesn't conform path [/event/jsonschema/1-0-2]")
    )
  }

  def e2 = {
    val schema = parse(
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
    val stubFile = JsonFile(Some("/path/to/schemas/com.acme/event/jsonschema"), "1-0-2", schema)

    val command = SyncCommand("doesn't matter", "doesn't matter", new File("."))
    val failedStream = command.buildRequests("error".left, Stream(stubFile.right, stubFile.right, stubFile.right, stubFile.right))
    failedStream must beEqualTo(Stream("error".left))
  }

}
