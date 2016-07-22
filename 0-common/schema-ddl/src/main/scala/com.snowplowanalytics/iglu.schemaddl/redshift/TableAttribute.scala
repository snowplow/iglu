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

// Scalaz
import scalaz.NonEmptyList

/**
 * table_attributes are:
 * [ DISTSTYLE { EVEN | KEY | ALL } ]
 * [ DISTKEY ( column_name ) ]
 * [ [COMPOUND | INTERLEAVED ] SORTKEY ( column_name [, ...] ) ]
 */
sealed trait TableAttribute extends Ddl

sealed trait DiststyleValue extends Ddl
case object Even extends DiststyleValue { def toDdl = "EVEN" }
case object Key extends DiststyleValue { def toDdl = "KEY" }
case object All extends DiststyleValue { def toDdl = "ALL" }

sealed trait Sortstyle extends Ddl

case object CompoundSortstyle extends Sortstyle {
  def toDdl = "COMPOUND"
}

case object InterleavedSortstyle extends Sortstyle {
  def toDdl = "INTERLEAVED"
}

case class Diststyle(diststyle: DiststyleValue) extends TableAttribute {
  def toDdl = "DISTSTYLE " + diststyle.toDdl
}

// Don't confuse with redshift.DistKey which is applicable for columns
case class DistKeyTable(columnName: String) extends TableAttribute {
  def toDdl = s"DISTKEY ($columnName)"
}

// Don't confuse with redshift.SortKey which is applicable for columns
case class SortKeyTable(sortstyle: Option[Sortstyle], columns: NonEmptyList[String]) extends TableAttribute {
  def toDdl = sortstyle.map(_.toDdl + " ").getOrElse("") + "SORTKEY (" + columns.list.mkString(",") + ")"
}
