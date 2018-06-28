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

// specs2
import org.specs2.Specification

// Iglu
import com.snowplowanalytics.iglu.core.SchemaVer

// This library
import com.snowplowanalytics.iglu.schemaddl.Migration
import com.snowplowanalytics.iglu.schemaddl.sql.generators.MigrationGenerator


class MigrationGeneratorSpec extends Specification { def is = s2"""
  Check Redshift migrations generation
    generate addition migration with one new column $e1
    generate addition migration without visible changes $e2
    generate addition migration with three new columns $e3
  """

  val empty = ListMap.empty[String, Map[String, String]]

  def e1 = {
    val diff = Migration.SchemaDiff(ListMap("status" -> Map("type" -> "string")), empty, Set.empty[String])
    val schemaMigration = Migration("com.acme", "launch_missles", SchemaVer.Full(1,0,0), SchemaVer.Full(1,0,1), diff)
    val ddlMigration = MigrationGenerator.generateMigration(schemaMigration).render

    val result =
      """|-- WARNING: only apply this file to your database if the following SQL returns the expected:
         |--
         |-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = 'com_acme_launch_missles_1';
         |--  obj_description
         |-- -----------------
         |--  iglu:com.acme/launch_missles/jsonschema/1-0-0
         |--  (1 row)
         |
         |BEGIN TRANSACTION;
         |
         |  ALTER TABLE atomic.com_acme_launch_missles_1
         |    ADD COLUMN "status" VARCHAR(4096) ENCODE ZSTD;
         |
         |  COMMENT ON TABLE atomic.com_acme_launch_missles_1 IS 'iglu:com.acme/launch_missles/jsonschema/1-0-1';
         |
         |END TRANSACTION;""".stripMargin

    ddlMigration must beEqualTo(result)
  }

  def e2 = {
    val diff = Migration.SchemaDiff(empty, empty, Set.empty[String])
    val schemaMigration = Migration("com.acme", "launch_missles", SchemaVer.Full(2,0,0), SchemaVer.Full(2,0,1), diff)
    val ddlMigration = MigrationGenerator.generateMigration(schemaMigration).render

    val result =
      """|-- WARNING: only apply this file to your database if the following SQL returns the expected:
         |--
         |-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = 'com_acme_launch_missles_2';
         |--  obj_description
         |-- -----------------
         |--  iglu:com.acme/launch_missles/jsonschema/2-0-0
         |--  (1 row)
         |
         |BEGIN TRANSACTION;
         |
         |-- NO ADDED COLUMNS CAN BE EXPRESSED IN SQL MIGRATION
         |
         |  COMMENT ON TABLE atomic.com_acme_launch_missles_2 IS 'iglu:com.acme/launch_missles/jsonschema/2-0-1';
         |
         |END TRANSACTION;""".stripMargin

    ddlMigration must beEqualTo(result)
  }

  def e3 = {
    val newProps = ListMap(
      "status" -> Map("type" -> "string"),
      "launch_time" -> Map("type" -> "string", "format" -> "date-time"),
      "latitude" -> Map("type" -> "number", "minimum" -> "-90", "maximum" -> "90"),
      "longitude" -> Map("type" -> "number", "minimum" -> "-180", "maximum" -> "180"))

    val diff = Migration.SchemaDiff(newProps, empty, Set.empty[String])
    val schemaMigration = Migration("com.acme", "launch_missles", SchemaVer.Full(1,0,2), SchemaVer.Full(1,0,3), diff)
    val ddlMigration = MigrationGenerator.generateMigration(schemaMigration).render

    val result =
      """|-- WARNING: only apply this file to your database if the following SQL returns the expected:
         |--
         |-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = 'com_acme_launch_missles_1';
         |--  obj_description
         |-- -----------------
         |--  iglu:com.acme/launch_missles/jsonschema/1-0-2
         |--  (1 row)
         |
         |BEGIN TRANSACTION;
         |
         |  ALTER TABLE atomic.com_acme_launch_missles_1
         |    ADD COLUMN "status" VARCHAR(4096) ENCODE ZSTD;
         |  ALTER TABLE atomic.com_acme_launch_missles_1
         |    ADD COLUMN "launch_time" TIMESTAMP ENCODE ZSTD;
         |  ALTER TABLE atomic.com_acme_launch_missles_1
         |    ADD COLUMN "latitude" DOUBLE PRECISION ENCODE RAW;
         |  ALTER TABLE atomic.com_acme_launch_missles_1
         |    ADD COLUMN "longitude" DOUBLE PRECISION ENCODE RAW;
         |
         |  COMMENT ON TABLE atomic.com_acme_launch_missles_1 IS 'iglu:com.acme/launch_missles/jsonschema/1-0-3';
         |
         |END TRANSACTION;""".stripMargin

    ddlMigration must beEqualTo(result)
  }

}
