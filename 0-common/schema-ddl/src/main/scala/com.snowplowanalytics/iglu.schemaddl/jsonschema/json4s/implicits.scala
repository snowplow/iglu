package com.snowplowanalytics.iglu.schemaddl.jsonschema
package json4s

import org.json4s._

object implicits {

  import Formats._

  import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.Formats.allFormats

  /**
    * Type class instance allowing to convert json4s JValue
    * into JSON Schema class
    *
    * So far this is single implementation, but still need
    * to be imported into scope to get Schema.parse method work
    */
  implicit lazy val json4sToSchema: ToSchema[JValue] = new ToSchema[JValue] {
    def parse(json: JValue): Option[Schema] =
      json match {
        case _: JObject =>
          val mf = implicitly[Manifest[Schema]]
          Some(json.extract[Schema](allFormats, mf))
        case _          => None
      }
  }

  /**
    * Type class instance allowing to convert [[Schema]] to JValue
    *
    * So far this is single implementation, but still need
    * to be imported into scope to get Schema.parse method work
    */
  implicit lazy val json4sFromSchema: FromSchema[JValue] = new FromSchema[JValue] {
    def normalize(schema: Schema): JValue =
      Extraction.decompose(schema)
  }
}
