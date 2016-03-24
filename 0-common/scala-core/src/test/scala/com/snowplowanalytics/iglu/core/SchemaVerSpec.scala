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
package com.snowplowanalytics.iglu.core

import org.specs2.Specification

class SchemaVerSpec extends Specification { def is = s2"""
  Specification for SchemaVer
    validate SchemaVer $e1
    extract correct SchemaVer $e2
    fail to validate zero in MODEL $e3
    fail to validate preceding zero in REVISION $e4
  """

  def e1 =
    SchemaVer.isValid("2-42-0") must beTrue

  def e2 =
    SchemaVer.parse("1-12-1") must beSome(SchemaVer(1,12,1))

  def e3 =
    SchemaVer.isValid("0-12-1") must beFalse

  def e4 =
    SchemaVer.isValid("1-02-1") must beFalse
}
