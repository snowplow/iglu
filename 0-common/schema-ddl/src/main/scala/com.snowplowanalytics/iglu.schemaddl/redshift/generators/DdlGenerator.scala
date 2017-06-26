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
package redshift
package generators

// Scalaz
import scalaz._

// Scala
import scala.annotation.tailrec

// Iglu core
import com.snowplowanalytics.iglu.core.SchemaKey

// This project
import EncodeSuggestions._
import TypeSuggestions._

/**
 * Generates a Redshift DDL File from a Flattened JsonSchema
 */
object DdlGenerator {

  /**
   * Make a DDL header from the self-describing info
   *
   * @param schemaKey self-describing info
   * @param schemaName optional schema name
   * @return SQL comment
   */
  def getTableComment(tableName: String, schemaName: Option[String], schemaKey: SchemaKey): CommentOn = {
    val schema = schemaName.map(_ + ".").getOrElse("")
    CommentOn(schema + tableName, schemaKey.toSchemaUri)
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
   * Generates Redshift CreateTable object with all columns, attributes and constraints
   *
   * @param flatSchema flat schema produced by the Schema flattening process
   * @param name table name
   * @param dbSchema optional redshift schema name
   * @param rawMode do not produce any Snowplow specific columns (like root_id)
   * @param size default length for VARCHAR
   * @return CreateTable object with all data about table creation
   */
  def generateTableDdl(
      flatSchema: FlatSchema,
      name: String,
      dbSchema: Option[String],
      size: Int,
      rawMode: Boolean = false)
  : CreateTable = {

    val columns = getColumnsDdl(flatSchema.elems, flatSchema.required, size)
                    .toList
                    .sortBy(c => (-c.columnConstraints.size, c.columnName))
    
    if (rawMode) getRawTableDdl(dbSchema, name, columns)
    else getAtomicTableDdl(dbSchema, name, columns)
  }

  // Columns with data taken from self-describing schema
  private[redshift] val selfDescSchemaColumns = List(
    Column("schema_vendor", RedshiftVarchar(128), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull))),
    Column("schema_name", RedshiftVarchar(128), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull))),
    Column("schema_format", RedshiftVarchar(128), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull))),
    Column("schema_version", RedshiftVarchar(128), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull)))
  )

  // Snowplow-specific columns
  private[redshift] val parentageColumns = List(
    Column("root_id", RedshiftChar(36), Set(CompressionEncoding(RawEncoding)), Set(Nullability(NotNull))),
    Column("root_tstamp", RedshiftTimestamp, Set(CompressionEncoding(LzoEncoding)), Set(Nullability(NotNull))),
    Column("ref_root", RedshiftVarchar(255), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull))),
    Column("ref_tree", RedshiftVarchar(1500), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull))),
    Column("ref_parent", RedshiftVarchar(255), Set(CompressionEncoding(RunLengthEncoding)), Set(Nullability(NotNull)))
  )


  /**
   * Generate DDL for atomic (with Snowplow-specific columns and attributes) table
   *
   * @param dbSchema optional redshift schema name
   * @param name table name
   * @param columns list of generated DDLs for columns
   * @return full CREATE TABLE statement ready to be rendered
   */
  private def getAtomicTableDdl(dbSchema: Option[String], name: String, columns: List[Column]): CreateTable = {
    val schema           = dbSchema.getOrElse("atomic")
    val fullTableName    = schema + "." + name
    val tableConstraints = Set[TableConstraint](RedshiftDdlDefaultForeignKey(schema))
    val tableAttributes  = Set[TableAttribute]( // Snowplow-specific attributes
      Diststyle(Key),
      DistKeyTable("root_id"),
      SortKeyTable(None, NonEmptyList("root_tstamp"))
    )
    
    CreateTable(
      fullTableName,
      selfDescSchemaColumns ++ parentageColumns ++ columns,
      tableConstraints,
      tableAttributes
    )
  }

  /**
   * Generate DDL forraw (without Snowplow-specific columns and attributes) table
   *
   * @param dbSchema optional redshift schema name
   * @param name table name
   * @param columns list of generated DDLs for columns
   * @return full CREATE TABLE statement ready to be rendered
   */
  private def getRawTableDdl(dbSchema: Option[String], name: String, columns: List[Column]): CreateTable = {
    val fullTableName = dbSchema.map(_ + "." + name).getOrElse(name)
    CreateTable(fullTableName, columns)
  }

  /**
   * Get DDL for Foreign Key for specified schema
   *
   * @param schemaName Redshift's schema
   * @return ForeignKey constraint
   */
  private def RedshiftDdlDefaultForeignKey(schemaName: String) = {
    val reftable = RefTable(schemaName + ".events", Some("event_id"))
    ForeignKeyTable(NonEmptyList("root_id"), reftable)
  }

  /**
   * Processes the Map of Data elements pulled from the JsonSchema and
   * generates DDL object for it with it's name, constrains, attributes
   * data type, etc
   *
   * @param flatDataElems The Map of Schema keys -> attributes which need to
   *                      be processed
   * @param required required fields to decide which columns are nullable
   * @return a list of Column DDLs
   */
  private[schemaddl] def getColumnsDdl(
      flatDataElems: PropertyList,
      required: Set[String],
      varcharSize: Int)
  : Iterable[Column] = {

    // Process each key pair in the map
    for {
      (columnName, properties) <- flatDataElems
    } yield {
      val dataType = getDataType(properties, varcharSize, columnName)
      val encoding = getEncoding(properties, dataType, columnName)
      val constraints =    // only "NOT NULL" now
        if (checkNullability(properties, required.contains(columnName))) Set.empty[ColumnConstraint]
        else Set[ColumnConstraint](Nullability(NotNull))
      Column(columnName, dataType, columnAttributes = Set(encoding), columnConstraints = constraints)
    }
  }

  // List of data type suggestions
  val dataTypeSuggestions: List[DataTypeSuggestion] = List(
    complexEnumSuggestion,
    productSuggestion,
    timestampSuggestion,
    dateSuggestion,
    arraySuggestion,
    integerSuggestion,
    numberSuggestion,
    booleanSuggestion,
    charSuggestion,
    uuidSuggestion,
    varcharSuggestion
  )

  // List of compression encoding suggestions
  val encodingSuggestions: List[EncodingSuggestion] = List(lzoSuggestion)


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
  @tailrec private[schemaddl] def getDataType(
      properties: Map[String, String],
      varcharSize: Int,
      columnName: String,
      suggestions: List[DataTypeSuggestion] = dataTypeSuggestions)
  : DataType = {

    suggestions match {
      case Nil => RedshiftVarchar(varcharSize) // Generic
      case suggestion :: tail => suggestion(properties, columnName) match {
        case Some(format) => format
        case None => getDataType(properties, varcharSize, columnName, tail)
      }
    }
  }

  /**
   * Takes each suggestion out of ``compressionEncodingSuggestions`` and
   * decide whether current properties satisfy it, then return the compression
   * encoding.
   * If nothing suggested LZO Encoding returned as default
   *
   * @param properties is a string we need to recognize
   * @param dataType redshift data type for current column
   * @param columnName to produce warning
   * @param suggestions list of functions can recognize encode type
   * @return some format or none if nothing suites
   */
  @tailrec private[schemaddl] def getEncoding(
      properties: Map[String, String],
      dataType: DataType,
      columnName: String,
      suggestions: List[EncodingSuggestion] = encodingSuggestions)
  : CompressionEncoding = {

    suggestions match {
      case Nil => CompressionEncoding(LzoEncoding) // LZO is default for user-generated
      case suggestion :: tail => suggestion(properties, dataType, columnName) match {
        case Some(encoding) => CompressionEncoding(encoding)
        case None => getEncoding(properties, dataType, columnName, tail)
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
