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
 * column_constraints are:
 * [ { NOT NULL | NULL } ]
 * [ { UNIQUE  |  PRIMARY KEY } ]
 * [ REFERENCES reftable [ ( refcolumn ) ] ]
 */
sealed trait ColumnConstraint extends Ddl

sealed trait NullabilityValue extends Ddl
case object Null extends NullabilityValue { def toDdl = "NULL" }
case object NotNull extends NullabilityValue { def toDdl = "NOT NULL" }

case class Nullability(value: NullabilityValue) extends ColumnConstraint {
  def toDdl = value.toDdl
}

sealed trait KeyConstraintValue extends Ddl
case object Unique extends KeyConstraintValue { def toDdl = "UNIQUE" }
case object PrimaryKey extends KeyConstraintValue { def toDdl = "PRIMARY KEY" }

case class KeyConstaint(value: KeyConstraintValue) extends ColumnConstraint {
  def toDdl = value.toDdl
}

