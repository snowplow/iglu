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
package generators

// Scala
import scala.collection.immutable.ListMap

// Specs2
import org.specs2.Specification

// scalaz
import scalaz.NonEmptyList

// This library
import com.snowplowanalytics.iglu.schemaddl.FlatSchema
import com.snowplowanalytics.iglu.schemaddl.sql._

class DdlGeneratorSpec extends Specification { def is = s2"""
  Check DDL generation specification
    Generate correct DDL for atomic table $e1
    Generate correct DDL for with runlength encoding for booleans $e2
  """

  def e1 = {
    val flatSchema = FlatSchema(
      ListMap(
        "foo" -> Map("type" -> "string", "maxLength" -> "30"),
        "bar" -> Map("enum" -> "one,two,three")
      ),
      Set("foo")
    )

    val resultDdl = CreateTable(
      "atomic.launch_missles",
      DdlGenerator.selfDescSchemaColumns ++
      DdlGenerator.parentageColumns ++
      List(
        Column("foo",SqlVarchar(30),Set(CompressionEncoding(ZstdEncoding)),Set(Nullability(NotNull))),
        Column("bar",SqlVarchar(5),Set(CompressionEncoding(ZstdEncoding)),Set())
      ),
      Set(ForeignKeyTable(NonEmptyList("root_id"),RefTable("atomic.events",Some("event_id")))),
      Set(Diststyle(Key), DistKeyTable("root_id"),SortKeyTable(None,NonEmptyList("root_tstamp")))
    )

    val ddl = DdlGenerator.generateTableDdl(flatSchema, "launch_missles", None, 4096)

    ddl must beEqualTo(resultDdl)
  }

  def e2 = {
    val flatSchema = FlatSchema(
      ListMap(
        "foo" -> Map("type" -> "boolean"),
        "baz" -> Map("type" -> "boolean"),
        "bar" -> Map("enum" -> "one,two,three")
      ),
      Set("foo")
    )

    val resultDdl = CreateTable(
      "atomic.launch_missles",
      DdlGenerator.selfDescSchemaColumns ++
      DdlGenerator.parentageColumns ++
      List(
        Column("foo",SqlBoolean,Set(CompressionEncoding(RunLengthEncoding)),Set(Nullability(NotNull))),
        Column("bar",SqlVarchar(5),Set(CompressionEncoding(ZstdEncoding)),Set()),
        Column("baz",SqlBoolean,Set(CompressionEncoding(RunLengthEncoding)),Set())
      ),
      Set(ForeignKeyTable(NonEmptyList("root_id"),RefTable("atomic.events",Some("event_id")))),
      Set(Diststyle(Key), DistKeyTable("root_id"),SortKeyTable(None,NonEmptyList("root_tstamp")))
    )

    val ddl = DdlGenerator.generateTableDdl(flatSchema, "launch_missles", None, 4096)

    ddl must beEqualTo(resultDdl)
  }

}
