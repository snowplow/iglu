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
package json4s

// json4s
import org.json4s._
import org.json4s.JsonDSL._

/**
 * Example of serializers for JSON Schema
 */
object Json4sIgluCodecs {

  // Public formats. Import it
  lazy val formats: Formats = schemaFormats + SchemaSerializer + DataSerializer

  // Local formats
  private implicit val schemaFormats: Formats = DefaultFormats + SchemaVerSerializer

  /**
   * Extract SchemaVer (*-*-*) from JValue
   */
  object SchemaVerSerializer extends CustomSerializer[SchemaVer.Full](_ => (
    {
      case JString(version) => SchemaVer.parse(version) match {
        case Right(schemaVer: SchemaVer.Full) => schemaVer
        case _ => throw new MappingException("Can't convert " + version + " to SchemaVer")
      }
      case x => throw new MappingException("Can't convert " + x + " to SchemaVer")
    },

    {
      case x: SchemaVer => JString(x.asString)
    }
  ))

  /**
   * Extract `SchemaKey` from `self` key and remaining as Schema body
   */
  object SchemaSerializer extends CustomSerializer[SelfDescribingSchema[JValue]](_ => (
    {
      case fullSchema: JObject =>
        val schemaMap = SchemaMap((fullSchema \ "self").extract[SchemaKey])
        val schema = removeSelf(fullSchema)
        SelfDescribingSchema(schemaMap, schema)
      case _ => throw new MappingException("Not an JSON object")
    },

    {
      case SelfDescribingSchema(self, schema: JValue) =>
        (("self", Extraction.decompose(self.schemaKey)): JObject).merge(schema)
    }
    ))

  /**
   * Extract `SchemaKey` from string and data from data key
   */
  object DataSerializer extends CustomSerializer[SelfDescribingData[JValue]](_ => (
    {
      case fullInstance: JObject =>
        val schemaKey = (fullInstance \ "schema").extractOpt[String].flatMap(SchemaKey.fromUri(_).right.toOption).getOrElse {
          throw new MappingException("Does not contain schema key with valid Schema URI")
        }
        val data = fullInstance \ "data" match {
          case JNothing => throw new MappingException("Does not contain data")
          case json: JValue => json
        }
        SelfDescribingData(schemaKey, data)
      case _ => throw new MappingException("Not an JSON object")
    },
    
    {
      case SelfDescribingData(key, data: JValue) =>
        JObject(("schema", JString(key.toSchemaUri)) :: ("data", data) :: Nil)
    }
    ))

  def removeSelf(json: JValue): JValue = json match {
    case JObject(fields) =>
      fields.filterNot {
        case ("self", JObject(keys)) => intersectsWithSchemakey(keys)
        case _ => false
      }
    case jvalue => jvalue
  }

  private def intersectsWithSchemakey(fields: List[JField]): Boolean =
    fields.map(_._1).toSet.diff(Set("name", "vendor", "format", "version")).isEmpty
}
