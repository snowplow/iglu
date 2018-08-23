/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
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
package bigquery

class SuggestionSpec extends org.specs2.Specification { def is = s2"""
  stringSuggestion produces nullable field even with required $e1
  stringSuggestion produces nothing for union type $e2
  """

  def e1 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": ["null", "string"]}
      """.stripMargin)

    val expected = Field("foo", Type.String, Mode.Nullable)

    Suggestion.stringSuggestion(input, true).map(_.apply("foo")) must beSome(expected)
  }

  def e2 = {
    val input = SpecHelpers.parseSchema(
      """
        |{"type": ["integer", "string"]}
      """.stripMargin)

    Suggestion.stringSuggestion(input, true) must beNone
  }
}
