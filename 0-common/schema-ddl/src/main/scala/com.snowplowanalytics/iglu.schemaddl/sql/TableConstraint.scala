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
package com.snowplowanalytics.iglu.schemaddl.sql

// Scalaz
import scalaz.NonEmptyList

/**
 * table_constraints  are:
 * [ UNIQUE ( column_name [, ... ] ) ]
 * [ PRIMARY KEY ( column_name [, ... ] )  ]
 * [ FOREIGN KEY (column_name [, ... ] ) REFERENCES reftable [ ( refcolumn ) ]
 */
sealed trait TableConstraint extends Ddl

case class UniqueKeyTable(columns: NonEmptyList[String]) extends TableConstraint {
  def toDdl = s"UNIQUE (${columns.list.map(_.mkString(", "))})"
}

case class PrimaryKeyTable(columns: NonEmptyList[String]) extends TableConstraint {
  def toDdl = s"PRIMARY KEY (${columns.list.map(_.mkString(", "))})"
}

case class ForeignKeyTable(columns: NonEmptyList[String], reftable: RefTable) extends TableConstraint {
  def toDdl = s"FOREIGN KEY (${columns.list.mkString(",")}) ${reftable.toDdl}"
}

