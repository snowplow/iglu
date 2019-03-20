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

// Specs2
import org.specs2.Specification

import cats.data.NonEmptyList
import io.circe.literal._

// This library
import com.snowplowanalytics.iglu.schemaddl.SpecHelpers._
import com.snowplowanalytics.iglu.schemaddl.FlatSchema

// TODO: union type specs (string, object)

class DdlGeneratorSpec extends Specification { def is = s2"""
  Check DDL generation specification
    Generate correct DDL for atomic table $e1
    Generate correct DDL for with runlength encoding for booleans $e2
  """

  def e1 = {
    val flatSchema = FlatSchema(
      Set(
        "/foo".jsonPointer -> json"""{"type": "string", "maxLength": 30}""".schema,
        "/bar".jsonPointer -> json"""{"enum": ["one","two","three",null]}""".schema
      ),
      Set("/foo".jsonPointer),
      Set.empty
    )

    val resultDdl = CreateTable(
      "atomic.launch_missles",
      DdlGenerator.selfDescSchemaColumns ++
      DdlGenerator.parentageColumns ++
      List(
        Column("foo",RedshiftVarchar(30),Set(CompressionEncoding(ZstdEncoding)),Set(Nullability(NotNull))),
        Column("bar",RedshiftVarchar(5),Set(CompressionEncoding(ZstdEncoding)),Set()),
      ),
      Set(ForeignKeyTable(NonEmptyList.of("root_id"),RefTable("atomic.events",Some("event_id")))),
      Set(Diststyle(Key), DistKeyTable("root_id"),SortKeyTable(None,NonEmptyList.of("root_tstamp")))
    )

    val ddl = DdlGenerator.generateTableDdl(flatSchema, "launch_missles", None, 4096, false)

    ddl must beEqualTo(resultDdl)
  }

  def e2 = {
    val flatSchema = FlatSchema(
      Set(
        "/foo".jsonPointer -> json"""{"type": "boolean"}""".schema,
        "/baz".jsonPointer -> json"""{"type": "boolean"}""".schema,
        "/bar".jsonPointer -> json"""{"enum": ["one","two","three"]}""".schema
      ),
      Set("/foo".jsonPointer),
      Set.empty
    )

    val resultDdl = CreateTable(
      "atomic.launch_missles",
      DdlGenerator.selfDescSchemaColumns ++
      DdlGenerator.parentageColumns ++
      List(
        Column("bar",RedshiftVarchar(5),Set(CompressionEncoding(ZstdEncoding)),Set(Nullability(NotNull))),
        Column("baz",RedshiftBoolean,Set(CompressionEncoding(RunLengthEncoding)),Set(Nullability(NotNull))),
        Column("foo",RedshiftBoolean,Set(CompressionEncoding(RunLengthEncoding)),Set(Nullability(NotNull)))
      ),
      Set(ForeignKeyTable(NonEmptyList.of("root_id"),RefTable("atomic.events",Some("event_id")))),
      Set(Diststyle(Key), DistKeyTable("root_id"),SortKeyTable(None,NonEmptyList.of("root_tstamp")))
    )

    val ddl = DdlGenerator.generateTableDdl(flatSchema, "launch_missles", None, 4096, false)

    ddl must beEqualTo(resultDdl)
  }

}
