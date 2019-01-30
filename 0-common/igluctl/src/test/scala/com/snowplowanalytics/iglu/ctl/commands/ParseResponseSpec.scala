/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
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

// specs2
import org.specs2.Specification

// Scala
import scalaj.http.HttpResponse

// Snowplow
import com.snowplowanalytics.iglu.ctl.commands.Pull

class ParseResponseSpec extends Specification {

  def is = s2"""
  The function `parseResponse` should
    successfully parse a valid schema into a SelfDesribingSchema $e1
    return a ParseError if the schema is not a valid Iglu schema $e2
  """

  val validResponseBody =
    """
   |[
   |  {
   |	  "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
   |	  "description": "Schema for a single HTTP cookie, as defined in RFC 6265",
   |	  "self": {
   |		  "vendor": "org.ietf",
   |		  "name": "http_cookie",
   |		  "format": "jsonschema",
   |		  "version": "1-0-0"
   |	  },
   |
   |	  "type": "object",
   |	  "properties": {
   |		  "name": {
   |			  "type": "string",
   |			  "maxLength" : 4096
   |		  },
   |		  "value": {
   |			  "type": ["string", "null"],
   |			  "maxLength" : 4096
   |		  }
   |	  },
   |	  "required": ["name", "value"],
   |	  "additionalProperties": false
   |  }
   |]""".stripMargin

  val invalidResponseBody =
    """
   |[
   |  {
   |    "type": "object",
   |    "properties": {
   |      "name": {
   |        "type": "string",
   |        "maxLength": 4096
   |      },
   |      "value": {
   |        "type": [
   |          "string",
   |          "null"
   |        ],
   |        "maxLength": 4096
   |      }
   |    },
   |    "required": [
   |      "name",
   |      "value"
   |    ],
   |    "additionalProperties": false
   |  }
   |]""".stripMargin

  def e1 = {
    val response = HttpResponse(validResponseBody, 200, Map("header" -> IndexedSeq("header")))
    val parsedResponse = Pull.parseResponse(response)

    parsedResponse.head must beRight
  }

  def e2 = {
    val response = HttpResponse(invalidResponseBody, 200, Map("header" -> IndexedSeq("header")))
    val parsedResponse = Pull.parseResponse(response)

    parsedResponse.head must beLeft
  }
}