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
package generators

// Scalaz
import scalaz._

// Iglu core
import com.snowplowanalytics.iglu.core.SchemaMap

// This project
import sql.generators.SqlTypeSuggestions.DataTypeSuggestion


/**
 * Generates a Redshift DDL File from a Flattened JsonSchema
 */
abstract class SqlDdlGenerator[T <: Ddl] {

  /**
   * Make a DDL header from the self-describing info
   *
   * @param schemaMap self-describing info
   * @param schemaName optional schema name
   * @return SQL comment
   */
  def getTableComment(tableName: String, schemaName: Option[String], schemaMap: SchemaMap): CommentOn = {
    val schema = schemaName.map(_ + ".").getOrElse("")
    CommentOn(schema + tableName, schemaMap.toSchemaUri)
  }

  /**
   * Make a DDL header from the file name
   *
   * @param tableName table name
   * @param schemaName optional DB schema name
   * @param fileName JSON Schema file name
   * @return SQL comment
   */
  def getTableComment(tableName: String, schemaName: Option[String], fileName: String): CommentOn = {
    val schema = schemaName.map(_ + ".").getOrElse("")
    CommentOn(schema + tableName, "Source: " + fileName)
  }

  /**
   * Generate DDL forraw (without Snowplow-specific columns and attributes) table
   *
   * @param dbSchema optional redshift schema name
   * @param name table name
   * @param columns list of generated DDLs for columns
   * @return full CREATE TABLE statement ready to be rendered
   */
  private[schemaddl] def getRawTableDdl(dbSchema: Option[String], name: String, columns: List[Column[T]]): CreateTable[T] = {
    val fullTableName = dbSchema.map(_ + "." + name).getOrElse(name)
    CreateTable(fullTableName, columns)
  }

  /**
   * Get DDL for Foreign Key for specified schema
   *
   * @param schemaName Redshift's schema
   * @return ForeignKey constraint
   */
  protected def DdlDefaultForeignKey(schemaName: String) = {
    val reftable = RefTable(schemaName + ".events", Some("event_id"))
    ForeignKeyTable(NonEmptyList("root_id"), reftable)
  }

  /**
   * Takes each suggestion out of ``dataTypeSuggesions`` and decide whether
   * current properties satisfy it, then return the data type
   * If nothing suggested VARCHAR with ``varcharSize`` returned as default
   *
   * @param properties is a string we need to recognize
   * @param varcharSize default size for unhandled properties and strings
   *                    without any data about length
   * @param columnName to produce warning
   * @param suggestions list of functions can recognize encode type
   * @return some format or none if nothing suites
   */
  private[schemaddl] def getDataType(
      properties: Map[String, String],
      varcharSize: Int,
      columnName: String,
      suggestions: List[DataTypeSuggestion] = SqlDdlGenerator.dataTypeSuggestions)
  : DataType[Ddl] = {

    suggestions match {
      case Nil => SqlVarchar(varcharSize) // Generic
      case suggestion :: tail => suggestion(properties, columnName) match {
        case Some(format) => format
        case None => getDataType(properties, varcharSize, columnName, tail)
      }
    }
  }

  /**
   * Check whether field can be null.
   * Priority of factors:
   * - "null" in type
   * - null in enum
   * - property is in required array
   *
   * @param properties hash map of JSON Schema properties for primitive type
   * @param required whether this field listed in required array
   * @return nullable or not
   */
  private[schemaddl] def checkNullability(properties: Map[String, String], required: Boolean): Boolean = {
    (properties.get("type"), properties.get("enum")) match {
      case (Some(types), _) if types.contains("null") => true
      case (_, Some(enum)) if enum.split(",").toList.contains("null") => true
      case _ => !required
    }
  }

}

object SqlDdlGenerator {
  // Columns with data taken from self-describing schema
  val selfDescSchemaColumns = List(
    Column[Ddl]("schema_vendor", SqlVarchar(128), Set(Nullability[Ddl](NotNull))),
    Column[Ddl]("schema_name", SqlVarchar(128), Set(Nullability[Ddl](NotNull))),
    Column[Ddl]("schema_format", SqlVarchar(128), Set(Nullability[Ddl](NotNull))),
    Column[Ddl]("schema_version", SqlVarchar(128), Set(Nullability[Ddl](NotNull)))
  )

  // Snowplow-specific columns
  val parentageColumns = List(
    Column[Ddl]("root_id", SqlChar(36), Set(Nullability(NotNull))),
    Column[Ddl]("root_tstamp", SqlTimestamp, Set(Nullability(NotNull))),
    Column[Ddl]("ref_root", SqlVarchar(255), Set(Nullability(NotNull))),
    Column[Ddl]("ref_tree", SqlVarchar(1500), Set(Nullability(NotNull))),
    Column[Ddl]("ref_parent", SqlVarchar(255), Set(Nullability(NotNull)))
  )

  import SqlTypeSuggestions._
  // List of data type suggestions
  lazy val dataTypeSuggestions: List[DataTypeSuggestion] = List(
    complexEnumSuggestion,
    timestampSuggestion,
    dateSuggestion,
    arraySuggestion,
    integerSuggestion,
    numberSuggestion,
    booleanSuggestion,
    charSuggestion,
    uuidSuggestion,
    varcharSuggestion,
    productSuggestion
  )
}
