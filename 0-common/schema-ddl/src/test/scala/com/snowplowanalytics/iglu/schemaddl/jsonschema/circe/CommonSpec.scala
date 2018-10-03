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
package com.snowplowanalytics.iglu.schemaddl.jsonschema.circe

// circe
import io.circe.literal._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties
import implicits._

// specs2
import org.specs2.Specification

class CommonSpec extends Specification { def is = s2"""
  Check JSON Schema common properties
    parse string-typed Schema $e1
    parse union-typed Schema $e2
    skip unknown type $e3
    parse oneOf property $e4
  """

  def e1 = {

    val schema = json"""{ "type": "string"}"""

    Schema.parse(schema) must beSome(Schema(`type` = Some(CommonProperties.Type.String)))
  }


  def e2 = {

    val schema = json"""{ "type": ["string", "null"]}"""

    Schema.parse(schema) must beSome(Schema(`type` = Some(CommonProperties.Type.Union(List(CommonProperties.Type.String, CommonProperties.Type.Null)))))
  }

  def e3 = {

    val schema = json"""
        {
          "type": ["unknown", "string"],
          "format": "ipv4"
        }
      """

    Schema.parse(schema) must beNone
  }
  
  def e4 = {
    
    val schema = json"""
        {
        	 "type": "object",
         		"oneOf": [

        			{
        				"properties": {
        					"embedded": {
        						"type": "object",
        						"properties": {
        							"path": {
        								"type": "string"
        							}
        						},
        						"required": ["path"],
        						"additionalProperties": false
        					}
        				},
        				"required": ["embedded"],
        				"additionalProperties": false
        			},

        			{
        				"properties": {
        					"http": {
        						"type": "object",
        						"properties": {
        							"uri": {
        								"type": "string",
        								"format": "uri"
        							},
        							"apikey": {
        								"type": ["string", "null"]
        							}
        						},
        						"required": ["uri"],
        						"additionalProperties": false
        					}
        				},
        				"required": ["http"],
        				"additionalProperties": false
        			}
          ]
        }

      """

    Schema.parse(schema).flatMap(_.oneOf) must beSome.like {
      case oneOf => oneOf.value.length must beEqualTo(2)
    }
  }
}
