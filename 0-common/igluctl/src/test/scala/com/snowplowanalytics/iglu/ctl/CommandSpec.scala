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
import java.util.UUID

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.{ unknownFormats, rootObject, allLintersMap }

// specs2
import org.specs2.Specification

// File(".") used everywhere because directory must be available for read
class CommandSpec extends Specification { def is = s2"""
  CLI specification
    correctly extract lint command class $e1
    correctly extract static push command class $e2
    correctly extract static s3cp command class $e3
    correctly extract lint command class (--skip-checks) $e4
  """

  def e1 = {
    val lint = Command
      .cliParser
      .parse("lint .".split(" "), Command())
      .flatMap(_.toCommand)

    lint must beSome(LintCommand(new File("."), false, allLintersMap.values.toList))
  }

  def e2 = {
    val staticPush = Command
      .cliParser
      .parse("static push .. http://54.165.217.26:8081/ 1af851ab-ef1b-4109-a8e2-720ac706334c --public".split(" "), Command())
      .flatMap(_.toCommand)

    val url = PushCommand.parseRegistryRoot("http://54.165.217.26:8081/").fold(x => throw x, identity)
    staticPush must beSome(PushCommand(url, UUID.fromString("1af851ab-ef1b-4109-a8e2-720ac706334c"), new File(".."), true))
  }

  def e3 = {
    val staticS3cp = Command
      .cliParser
      .parse("static s3cp .. anton-enrichment-test --s3path schemas --region us-east-1".split(" "), Command())
      .flatMap(_.toCommand)

    staticS3cp must beSome(S3cpCommand(new File(".."), "anton-enrichment-test", Some("schemas"), None, None, None, Some("us-east-1")))
  }

  def e4 = {
    val lint = Command
      .cliParser
      .parse("lint . --skip-checks unknownFormats,rootObject".split(" "), Command())
      .flatMap(_.toCommand)

    val skippedChecks = List(unknownFormats, rootObject)

    lint must beSome(LintCommand(new File("."), false, allLintersMap.values.toList.diff(skippedChecks)))
  }
}
