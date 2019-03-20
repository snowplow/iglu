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
package generators

// Iglu Core
import com.snowplowanalytics.iglu.core._

// This library
import com.snowplowanalytics.iglu.schemaddl.Migration
import com.snowplowanalytics.iglu.schemaddl.StringUtils._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Schema, Pointer }

// This library
import DdlGenerator._

/**
 * Module containing all logic to generate DDL files with information required
 * to migration from one version of Schema to another
 */
object MigrationGenerator {

  /**
   * Generate full ready to be rendered DDL file containing all migration
   * statements and additional data like previous version of table
   *
   * @param migration common JSON Schema migration object with
   *                  path (from-to) and diff
   * @param varcharSize size VARCHARs by default
   * @param tableSchema DB schema for table (atomic by default)
   * @return DDL file containing list of statements ready to be printed
   */
  def generateMigration(migration: Migration, varcharSize: Int, tableSchema: Option[String]): DdlFile = {
    val schemaMap     = SchemaMap(migration.vendor, migration.name, "jsonschema", migration.to)
    val oldSchemaUri  = SchemaMap(migration.vendor, migration.name, "jsonschema", migration.from).schemaKey.toSchemaUri
    val tableName     = getTableName(schemaMap)                            // e.g. com_acme_event_1
    val tableNameFull = tableSchema.map(_ + ".").getOrElse("") + tableName   // e.g. atomic.com_acme_event_1

    val transaction =
      if (migration.diff.added.nonEmpty)
        migration.diff.added.map {
          case (pointer, schema) =>
            buildAlterTable(tableNameFull, varcharSize, (pointer, schema))
        }
      else List(CommentBlock("NO ADDED COLUMNS CAN BE EXPRESSED IN SQL MIGRATION", 3))

    val header = getHeader(tableName, oldSchemaUri)
    val comment = CommentOn(tableNameFull, schemaMap.schemaKey.toSchemaUri)
    DdlFile(List(header, Empty, Begin(None, None), Empty) ++ transaction :+ Empty :+ comment :+ Empty :+ End)
  }

  /**
   * Generate comment block for for migration file with information about
   * previous version of table
   *
   * @param tableName name of migrating table
   * @param oldSchemaUri Schema URI extracted from internal database store
   * @return DDL statement with header
   */
  def getHeader(tableName: String, oldSchemaUri: String): CommentBlock =
    CommentBlock(Vector(
      "WARNING: only apply this file to your database if the following SQL returns the expected:",
      "",
      s"SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = '$tableName';",
      " obj_description",
      "-----------------",
      s" $oldSchemaUri",
      " (1 row)"))

  /**
   * Generate single ALTER TABLE statement for some new property
   *
   * @param tableName name of migrating table
   * @param varcharSize default size for VARCHAR
   * @param pair pair of property name and its Schema properties like
   *             length, maximum, etc
   * @return DDL statement altering single column in table
   */
  def buildAlterTable(tableName: String, varcharSize: Int, pair: (Pointer.SchemaPointer, Schema)): AlterTable =
    pair match {
      case (pointer, properties) =>
        val columnName = DdlGenerator.getName(pointer)
        val dataType = getDataType(properties, varcharSize, columnName)
        val encoding = getEncoding(properties, dataType, columnName)
        val nullable = if (properties.canBeNull) None else Some(Nullability(NotNull))
        AlterTable(tableName, AddColumn(snakeCase(columnName), dataType, None, Some(encoding), nullable))
    }
}