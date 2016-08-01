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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// specs2
import org.specs2.Specification

import json4s.Json4sToSchema._

class NumberSpec extends Specification { def is = s2"""
  Check JSON Schema number-specific properties
    correctly transform big BigInt to BigDecimal $e1
    correctly transform small BigInt to BigDecimal $e2
    correctly extract and compare number and integer equal values $e3
    don't extract non-numeric values (null) $e4
  """

  def e1 = {
    val json = parse(
      """
        |{
        |  "maximum": 9223372036854775807
        |}
      """.stripMargin)

    Schema.parse(json).flatMap(_.maximum).map(_.getAsDecimal) must beSome(BigDecimal(9223372036854775807L))
  }

  def e2 = {
    val json = parse(
      """
        |{
        |  "minimum": -9223372036854775806
        |}
      """.stripMargin)

    Schema.parse(json).flatMap(_.minimum).map(_.getAsDecimal) must beSome(BigDecimal(-9223372036854775806L))
  }

  def e3 = {
    val json = parse(
      """
        |{
        |  "minimum": 25,
        |  "maximum": 25.0
        |}
      """.stripMargin)

    val minimum = Schema.parse(json).flatMap(_.minimum).map(_.getAsDecimal).get
    val maximum = Schema.parse(json).flatMap(_.maximum).map(_.getAsDecimal).get

    minimum must beEqualTo(maximum)
  }

  def e4 = {
    val json = parse(
      """
        |{
        |  "minimum": null
        |}
      """.stripMargin)

    val minimum = Schema.parse(json).flatMap(_.minimum).map(_.getAsDecimal)

    minimum must beNone
  }

}
