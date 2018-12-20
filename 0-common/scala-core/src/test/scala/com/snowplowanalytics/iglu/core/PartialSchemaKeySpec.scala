/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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

class PartialSchemaKeySpec extends Specification { def is = s2"""
  fromUri parses full schema key as PartialSchemaKey $e1
  fromUri parses partial schema key $e2
  """

  def e1 = {
    val expected = PartialSchemaKey("com.acme.foo", "event", "jsonschema", SchemaVer(1,0,0))
    PartialSchemaKey.fromUri("iglu:com.acme.foo/event/jsonschema/1-0-0") must beRight(expected)
  }

  def e2 = {
    val expected = PartialSchemaKey("com.acme.foo", "event", "jsonschema", SchemaVer.Partial(Some(1),None,None))
    PartialSchemaKey.fromUri("iglu:com.acme.foo/event/jsonschema/1-?-?") must beRight(expected)
  }
}
