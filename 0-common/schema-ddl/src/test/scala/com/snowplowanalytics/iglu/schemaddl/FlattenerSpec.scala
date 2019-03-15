package com.snowplowanalytics.iglu.schemaddl

import org.specs2.Specification
import io.circe.literal._
import jsonschema.{Pointer, Schema}
import SpecHelpers._
import jsonschema.circe.implicits._
import jsonschema.json4s.implicits._
import org.json4s.jackson.JsonMethods.compact
import cats.implicits._

class FlattenerSpec extends Specification { def is = s2"""
  Try out traverse $e1
  """

  def e1 = {
    val schema = json"""
      {
      	"$$schema":"http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
      	"description":"Schema for an AWS Lambda Java context object, http://docs.aws.amazon.com/lambda/latest/dg/java-context-object.html",
      	"self":{
      		"vendor":"com.amazon.aws.lambda",
      		"name":"java_context",
      		"format":"jsonschema",
      		"version":"1-0-0"
      	},
      	"type":"object",
        "required": ["functionName", "clientContext"],
      	"properties":{
      		"functionName":{
      			"type":"string"
      		},
      		"logStreamName":{
      			"type":"string"
      		},
      		"awsRequestId":{
      			"type":"string"
      		},
      		"remainingTimeMillis":{
      			"type":"integer",
      			"minimum":0
      		},
      		"logGroupName":{
      			"type":"string"
      		},
      		"memoryLimitInMB":{
      			"type":"integer",
      			"minimum":0
      		},
      		"clientContext":{
      			"type":"object",
      			"required":["client"],
      			"properties":{
      				"client":{
      					"type":["object", "null"],
      					"required":["appPackageName"],
      					"properties":{
      						"appTitle":{
      							"type":"string"
      						},
      						"appVersionName":{
      							"type":"string"
      						},
      						"appVersionCode":{
      							"type":"string"
      						},
      						"appPackageName":{
      							"type":["integer"]
      						}
      					},
      					"additionalProperties":false
      				},
      				"custom":{
      					"type":"object",
      					"patternProperties":{
      						".*":{
      							"type":"string"
      						}
      					}
      				},
      				"environment":{
      					"type":"object",
      					"patternProperties":{
      						".*":{
      							"type":"string"
      						}
      					}
      				}
      			},
      			"additionalProperties":false
      		},
      		"identity":{
      			"type":"object",
      			"properties":{
      				"identityId":{
      					"type":"string"
      				},
      				"identityPoolId":{
      					"type":"string"
      				}
      			},
      			"additionalProperties":false
      		}
      	},
      	"additionalProperties":false
      }

      """.schema

    val flatSchema = FlatSchema.build(schema)

    println(flatSchema.show)
    println(flatSchema.required)
    import redshift.generators.DdlGenerator

    val result = DdlGenerator.generateTableDdl(flatSchema, "events", None, 4096, false)

    println(result.toDdl)
    ko
  }

}
