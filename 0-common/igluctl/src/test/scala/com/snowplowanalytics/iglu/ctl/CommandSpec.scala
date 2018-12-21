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
import java.nio.file.Paths
import java.util.UUID

// cats
import cats.syntax.either._

// decline
import com.monovore.decline.Help

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Linter.{ unknownFormats, rootObject, allLintersMap }

// specs2
import org.specs2.Specification

import com.snowplowanalytics.iglu.ctl.commands.Push

// File(".") used everywhere because directory must be available for read
class CommandSpec extends Specification { def is = s2"""
  Command.parse specification
    extracts lint command class $e1
    extracts static push command class $e2
    extracts static s3cp command class $e3
    extracts lint command class (--skip-checks) $e4
    fails to extract lint with unskippable checks specified $e5
  """

  def e1 = {
    val lint = Command.parse("lint .".split(" ").toList)

    lint must beRight(Command.Lint(Paths.get("."), false, List.empty))
  }

  def e2 = {
    val staticPush = Command.parse("static push .. http://54.165.217.26:8081/ 1af851ab-ef1b-4109-a8e2-720ac706334c --public".split(" ").toList)

    val url = Push.HttpUrl.parse("http://54.165.217.26:8081/").getOrElse(throw new RuntimeException("Invalid URI"))
    staticPush must beRight(Command.StaticPush(Paths.get(".."), url, UUID.fromString("1af851ab-ef1b-4109-a8e2-720ac706334c"), true))
  }

  def e3 = {
    val staticS3cp = Command
      .parse("static s3cp .. anton-enrichment-test --s3path schemas --region us-east-1".split(" ").toList)

    staticS3cp must beRight(Command.StaticS3Cp(Paths.get(".."), "anton-enrichment-test", Some("schemas"), None, None, None, Some("us-east-1")))
  }

  def e4 = {
    val lint = Command.parse("lint . --skip-checks unknownFormats,rootObject".split(" ").toList)

    val skippedChecks = List(unknownFormats, rootObject)

    lint must beRight(Command.Lint(Paths.get("."), false, skippedChecks))
  }

  def e5 = {
    val lint = Command.parse("lint . --skip-checks requiredPropertiesExist".split(" ").toList)

    lint must beLeft.like {
      case Help(errors, _, _, _) => errors must beEqualTo(List("Configuration is invalid: non-skippable linters [requiredPropertiesExist]"))
      case _ => ko("Invalid error message")
    }
  }
}
