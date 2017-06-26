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

/**
 * Data types
 * http://docs.aws.amazon.com/redshift/latest/dg/c_Supported_data_types.html
 */
sealed trait DataType extends Ddl

case object RedshiftTimestamp extends DataType {
  def toDdl = "TIMESTAMP"
}

case object RedshiftDate extends DataType {
  def toDdl = "DATE"
}

case object RedshiftSmallInt extends DataType {
  def toDdl = "SMALLINT"
}

case object RedshiftInteger extends DataType {
  def toDdl = "INT"
}

case object RedshiftBigInt extends DataType {
  def toDdl = "BIGINT"
}

case object RedshiftReal extends DataType {
  def toDdl = "REAL"
}

case object RedshiftDouble extends DataType {
  def toDdl = "DOUBLE PRECISION"
}

case class RedshiftDecimal(precision: Option[Int], scale: Option[Int]) extends DataType {
  def toDdl = (precision, scale) match {
    case (Some(p), Some(s)) => s"DECIMAL ($p, $s)"
    case _ => "DECIMAL"
  }
}

case object RedshiftBoolean extends DataType {
  def toDdl = "BOOLEAN"
}

case class RedshiftVarchar(size: Int) extends DataType {
  def toDdl = s"VARCHAR($size)"
}

case class RedshiftChar(size: Int) extends DataType {
  def toDdl = s"CHAR($size)"
}

// CUSTOM

/**
 * These predefined data types assembles into usual Redshift data types, but
 * can store additional information such as warnings.
 * Using to prevent output on DDL-generation step.
 */
case class ProductType(override val warnings: List[String]) extends DataType {
  def toDdl = "VARCHAR(4096)"
}

