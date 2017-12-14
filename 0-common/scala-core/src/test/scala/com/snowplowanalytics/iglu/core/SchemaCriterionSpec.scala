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

import org.specs2.Specification

// This library
import IgluCoreCommon._

class SchemaCriterionSpec extends Specification { def is = s2"""
  Specification for parsing SchemaCriterion
    parse simple correct criterion $e1
    parse criterion without SchemaVer $e2
    parse criterion 2-0-* $e9
    parse criterion 1-0-0 $e10
    fail to parse criterion with missing ADDITION $e3
    fail to parse criterion with 0 as MODEL $e8

  Specification for matching SchemaKeys with SchemaCriterions
    match by MODEL (2-*-*) $e4
    match initial Schema (*-0-0) $e5
    do not match not matching key (*-0-0) $e6
    filter correct entities (*-0-0) $e7
    """

  def e1 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/2-*-*"
    SchemaCriterion.parse(criterion) must beSome(
      SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 2))
  }

  def e2 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/*-*-*"
    SchemaCriterion.parse(criterion) must beSome(
      SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", None, None, None))
  }

  def e9 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/2-0-*"
    SchemaCriterion.parse(criterion) must beSome(
      SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", Some(2), Some(0), None))
  }

  def e10 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/1-0-0"
    SchemaCriterion.parse(criterion) must beSome(
      SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", Some(1), Some(0), Some(0)))
  }

  def e3 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/*-*-"
    SchemaCriterion.parse(criterion) must beNone
  }

  def e8 = {
    val criterion = "iglu:com.snowplowanalytics.snowplow/mobile_context/jsonschema/0-*-*"
    SchemaCriterion.parse(criterion) must beNone
  }

  def e4 = {
    val criterion = SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 2)
    val key = SchemaKey("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", SchemaVer(2, 1, 0))
    criterion.matches(key) must beTrue
  }

  def e5 = {
    val criterion = SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", None, Some(0), Some(0))
    val key = SchemaKey("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", SchemaVer(2, 0, 0))
    criterion.matches(key) must beTrue
  }

  def e6 = {
    val criterion = SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", None, Some(0), Some(0))
    val key = SchemaKey("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", SchemaVer(2, 1, 0))
    criterion matches key must beFalse
  }

  def e7 = {
    val criterion = SchemaCriterion("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", None, Some(0), Some(0))
    val keys = List(
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 2, 1, 0, "210"),
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 1, 0, 0, "100"),
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "avro", 2, 1, 0, "210"),
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 2, 0, 0, "200"),
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 1, 1, 1, "111")
    )
    val matched = criterion.pickFrom(keys)
    matched must containAllOf(Seq(
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 1, 0, 0, "100"),
      DescribedString("com.snowplowanalytics.snowplow", "mobile_context", "jsonschema", 2, 0, 0, "200")
    ))
  }
}
