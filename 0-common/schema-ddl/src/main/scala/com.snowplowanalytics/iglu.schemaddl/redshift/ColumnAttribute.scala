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
package com.snowplowanalytics.iglu.schemaddl.redshift

/**
 * column_attributes are:
 * [ DEFAULT default_expr ]
 * [ IDENTITY ( seed, step ) ]
 * [ ENCODE encoding ]
 * [ DISTKEY ]
 * [ SORTKEY ]
 */
sealed trait ColumnAttribute extends Ddl

case class Default(value: String) extends ColumnAttribute {
  def toDdl = s"DEFAULT $value"
}

case class Identity(seed: Int, step: Int) extends ColumnAttribute {
  def toDdl = s"IDENTITY ($seed, $step)"
}

case object DistKey extends ColumnAttribute {
  def toDdl = "DISTKEY"
}

case object SortKey extends ColumnAttribute {
  def toDdl = "SORTKEY"
}

/**
 * Compression encodings
 * http://docs.aws.amazon.com/redshift/latest/dg/c_Compression_encodings.html
 */
case class CompressionEncoding(value: CompressionEncodingValue) extends ColumnAttribute {
  def toDdl = s"ENCODE ${value.toDdl}"
}

sealed trait CompressionEncodingValue extends Ddl

case object RawEncoding extends CompressionEncodingValue { def toDdl = "RAW" }

case object ByteDictEncoding extends CompressionEncodingValue { def toDdl = "BYTEDICT" }

case object DeltaEncoding extends CompressionEncodingValue { def toDdl = "DELTA" }

case object Delta32kEncoding extends CompressionEncodingValue { def toDdl = "DELTA32K" }

case object LzoEncoding extends CompressionEncodingValue { def toDdl = "LZO" }

case object Mostly8Encoding extends CompressionEncodingValue { def toDdl = "MOSTLY8ENCODING" }

case object Mostly16Encoding extends CompressionEncodingValue { def toDdl = "MOSTLY16ENCODING" }

case object Mostly32Encoding extends CompressionEncodingValue { def toDdl = "MOSTLY32ENCODING" }

case object RunLengthEncoding extends CompressionEncodingValue { def toDdl = "RUNLENGTH" }

case object Text255Encoding extends CompressionEncodingValue { def toDdl = "TEXT255" }

case object Text32KEncoding extends CompressionEncodingValue { def toDdl = "TEXT32K" }
