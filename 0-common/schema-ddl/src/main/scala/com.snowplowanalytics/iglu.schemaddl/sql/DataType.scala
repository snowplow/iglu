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
package sql

trait DataType[T <: Ddl] extends Ddl

case object SqlTimestamp extends DataType[Ddl] {
  def toDdl = "TIMESTAMP"
}

case object SqlDate extends DataType[Ddl] {
  def toDdl = "DATE"
}

case object SqlSmallInt extends DataType[Ddl] {
  def toDdl = "SMALLINT"
}

case object SqlInteger extends DataType[Ddl] {
  def toDdl = "INT"
}

case object SqlBigInt extends DataType[Ddl] {
  def toDdl = "BIGINT"
}

case object SqlReal extends DataType[Ddl] {
  def toDdl = "REAL"
}

case object SqlDouble extends DataType[Ddl] {
  def toDdl = "DOUBLE PRECISION"
}

case class SqlDecimal(precision: Option[Int], scale: Option[Int]) extends DataType[Ddl] {
  def toDdl = (precision, scale) match {
    case (Some(p), Some(s)) => s"DECIMAL ($p, $s)"
    case _ => "DECIMAL"
  }
}

case object SqlBoolean extends DataType[Ddl] {
  def toDdl = "BOOLEAN"
}

case class SqlVarchar(size: Int) extends DataType[Ddl] {
  def toDdl = s"VARCHAR($size)"
}

case class SqlChar(size: Int) extends DataType[Ddl] {
  def toDdl = s"CHAR($size)"
}

// CUSTOM
/**
  * These predefined data types assembles into usual data types, but
  * can store additional information such as warnings.
  * Using to prevent output on DDL-generation step.
  */

case class ProductType(override val warnings: List[String]) extends DataType[Ddl] {
  def toDdl = "VARCHAR(4096)"
}