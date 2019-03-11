/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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
package redshift
package generators

import io.circe.literal._

import SpecHelpers._

// specs2
import org.specs2.Specification

class TypeSuggestionsSpec extends Specification { def is = s2"""
  Check type suggestions
    suggest decimal for multipleOf == 0.01 $e1
    suggest integer for multipleOf == 1 $e2
    handle invalid enum $e3
    recognize string,null maxLength == minLength as CHAR $e4
    recognize number with product type $e5
    recognize integer with product type $e6
    recognize timestamp $e7
    recognize full date $e8
  """

  def e1 = {
    val props = json"""{"type": "number", "multipleOf": 0.01}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftDecimal(Some(36), Some(2)))
  }

  def e2 = {
    val props = json"""{"type": "number", "multipleOf": 1}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftInteger)
  }

  def e3 = {
    val props = json"""{"type": "integer", "multipleOf": 1, "enum": [2,3,5,"hello",32]}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftVarchar(7))
  }

  def e4 = {
    val props = json"""{"type": ["string","null"], "minLength": "12", "maxLength": "12"}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftChar(12))
  }

  def e5 = {
    val props = json"""{"type": ["number","null"]}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftDouble)
  }

  def e6 = {
    val props = json"""{"type": ["integer","null"]}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftBigInt)
  }

  def e7 = {
    val props = json"""{"type": "string", "format": "date-time"}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftTimestamp)
  }

  def e8 = {
    val props = json"""{"type": "string", "format": "date"}""".schema
    DdlGenerator.getDataType(props, 16, "somecolumn") must beEqualTo(RedshiftDate)
  }
}
