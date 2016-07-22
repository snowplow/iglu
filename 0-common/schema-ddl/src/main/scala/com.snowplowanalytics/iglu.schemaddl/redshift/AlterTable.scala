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
 * Class holding data to alter some table with single [[AlterTableStatement]]
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
case class AlterTable(tableName: String, statement: AlterTableStatement) extends Statement {
  def toDdl = s"ALTER TABLE $tableName ${statement.toDdl}"
}

/**
 * Sum-type to represent some statement
 */
sealed trait AlterTableStatement extends Ddl

sealed trait DropModeValue extends Ddl
case object CascadeDrop extends DropModeValue { def toDdl = "CASCADE" }
case object RestrictDrop extends DropModeValue { def toDdl = "RESTRICT" }

case class DropMode(value: DropModeValue) extends Ddl {
  def toDdl = value.toDdl
}

case class AddConstraint(tableConstraint: TableConstraint) extends AlterTableStatement {
  def toDdl = s"ADD ${tableConstraint.toDdl}"
}

case class DropConstraint(constraintName: String, mode: Option[DropMode]) extends AlterTableStatement {
  def toDdl = s"DROP $constraintName${mode.map(" " + _.toDdl).getOrElse("")}"
}

case class OwnerTo(newOwner: String) extends AlterTableStatement {
  def toDdl = s"OWNER TO $newOwner"
}

case class RenameTo(newName: String) extends AlterTableStatement {
  def toDdl = s"RENAME TO $newName"
}

case class RenameColumn(columnName: String, newName: String) extends AlterTableStatement {
  def toDdl = s"RENAME COLUMN $columnName TO $newName"
}

case class AddColumn(
  columnName: String,
  columnType: DataType,
  default: Option[Default],
  encode: Option[CompressionEncoding],
  nullability: Option[Nullability]
) extends AlterTableStatement {
  def toDdl = {
    val attrs = List(nullability, encode, default).flatten.map(_.toDdl).mkString(" ")
    s"""ADD COLUMN "$columnName" ${columnType.toDdl} $attrs"""
  }
}

case class DropColumn(columnName: String, mode: Option[DropMode]) extends Ddl {
  def toDdl = s"DROP COLUMN $columnName${mode.map(" " + _.toDdl).getOrElse("")}"
}