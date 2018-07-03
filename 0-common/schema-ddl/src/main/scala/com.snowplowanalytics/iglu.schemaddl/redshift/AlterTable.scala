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

import com.snowplowanalytics.iglu.schemaddl.sql.{AlterTableStatement, ColumnAttribute, DataType, Ddl, Default, Nullability}

/**
 * Class holding data to alter some table with single [[AlterTableStatement]]
 *
 * @see http://docs.aws.amazon.com/redshift/latest/dg/r_ALTER_TABLE.html
 *
 * ALTER TABLE table_name
 * {
 * ADD table_constraint |
 * DROP CONSTRAINT constraint_name [ RESTRICT | CASCADE ] |
 * OWNER TO new_owner |
 * RENAME TO new_name |
 * RENAME COLUMN column_name TO new_name |
 * ADD [ COLUMN ] column_name column_type
 * [ DEFAULT default_expr ]
 * [ ENCODE encoding ]
 * [ NOT NULL | NULL ] |
 * DROP [ COLUMN ] column_name [ RESTRICT | CASCADE ] }
 *
 * where table_constraint is:
 *
 * [ CONSTRAINT constraint_name ]
 * { UNIQUE ( column_name [, ... ] )  |
 * PRIMARY KEY ( column_name [, ... ] ) |
 * FOREIGN KEY (column_name [, ... ] )
 * REFERENCES  reftable [ ( refcolumn ) ]}
 */
sealed trait RedShiftAlterTableStatement extends RedShiftDdl


private[redshift] case class AddColumn[T <: RedShiftDdl](
  columnName: String,
  columnType: DataType[Ddl],
  default: Option[Default[T]],
  encode: Option[ColumnAttribute[T]],
  nullability: Option[Nullability[T]]
) extends AlterTableStatement {
  def toDdl = {
    val attrs = List(nullability, encode, default).flatten.map(_.toDdl).mkString(" ")
    s"""ADD COLUMN "$columnName" ${columnType.toDdl} $attrs"""
  }
}
