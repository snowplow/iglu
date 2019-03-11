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
package com.snowplowanalytics.iglu.schemaddl.redshift
package generators

import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema

object EncodeSuggestions {
  /**
   * Type alias for function suggesting an compression encoding based on map of
   * JSON Schema properties
   */
  type EncodingSuggestion = (Schema, DataType, String) => Option[CompressionEncodingValue]

  // Suggest LZO Encoding for boolean, double precision and real
  val lzoSuggestion: EncodingSuggestion = (_, dataType, _) =>
    dataType match {
      case RedshiftBoolean => Some(RunLengthEncoding)
      case RedshiftDouble => Some(RawEncoding)
      case RedshiftReal => Some(RawEncoding)
      case _ => None
    }

  val zstdSuggestion: EncodingSuggestion = (properties, dataType, columnName) =>
    dataType match {
      case RedshiftVarchar(_) => Some(ZstdEncoding)
      case _ => None
    }
}
