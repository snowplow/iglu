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

// Scala
import com.snowplowanalytics.iglu.schemaddl.sql.{Ddl, NotNull, Nullability, SqlChar, SqlTimestamp, SqlVarchar}

import scala.annotation.tailrec

// Scalaz
import scalaz._

// This project
import EncodeSuggestions._
import sql.generators.SqlDdlGenerator
import redshift.generators.EncodeSuggestions.EncodingSuggestion
import com.snowplowanalytics.iglu.schemaddl.sql.{
  ColumnConstraint, DataType,
  TableAttribute, TableConstraint
}


/**
 * Generates a Redshift DDL File from a Flattened JsonSchema
 */
object DdlGenerator extends SqlDdlGenerator[RedShiftDdl] {

  /**
    * Generate DDL for atomic (with Snowplow-specific columns and attributes) table
    *
    * @param dbSchema optional redshift schema name
    * @param name table name
    * @param columns list of generated DDLs for columns
    * @return full CREATE TABLE statement ready to be rendered
    */
  private def getAtomicTableDdl(dbSchema: Option[String], name: String, columns: List[Column[RedShiftDdl]]): CreateTable[RedShiftDdl] = {
    val schema           = dbSchema.getOrElse("atomic")
    val fullTableName    = schema + "." + name
    val tableConstraints = Set[TableConstraint](DdlDefaultForeignKey(schema))
    val tableAttributes  = Set[TableAttribute[RedShiftDdl]]( // Snowplow-specific attributes
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
    * Takes each suggestion out of ``compressionEncodingSuggestions`` and
    * decide whether current properties satisfy it, then return the compression
    * encoding.
    * If nothing suggested ZSTD Encoding returned as default
    *
    * @param properties is a string we need to recognize
    * @param dataType redshift data type for current column
    * @param columnName to produce warning
    * @param suggestions list of functions can recognize encode type
    * @return some format or none if nothing suites
    */
  @tailrec protected[schemaddl] def getEncoding(
     properties: Map[String, String],
     dataType: DataType[Ddl],
     columnName: String,
     suggestions: List[EncodingSuggestion] = encodingSuggestions)
  : CompressionEncoding = {

    suggestions match {
      case Nil => CompressionEncoding(ZstdEncoding) // ZSTD is default for user-generated
      case suggestion :: tail => suggestion(properties, dataType, columnName) match {
        case Some(encoding) => CompressionEncoding(encoding)
        case None => getEncoding(properties, dataType, columnName, tail)
      }
    }
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
  : Iterable[Column[RedShiftDdl]] = {

    // Process each key pair in the map
    for {
      (columnName, properties) <- flatDataElems
    } yield {
      val dataType = getDataType(properties, varcharSize, columnName)
      val encoding = getEncoding(properties, dataType, columnName)
      val constraints =    // only "NOT NULL" now
        if (checkNullability(properties, required.contains(columnName))) Set.empty[ColumnConstraint[RedShiftDdl]]
        else Set[ColumnConstraint[RedShiftDdl]](Nullability(NotNull))
      Column[RedShiftDdl](columnName, dataType, Set(encoding),  constraints)
    }
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
  : CreateTable[RedShiftDdl] = {

    val columns = getColumnsDdl(flatSchema.elems, flatSchema.required, size)
      .toList
      .sortBy(c => (-c.columnConstraints.size, c.columnName))

    if (rawMode) getRawTableDdl(dbSchema, name, columns)
    else getAtomicTableDdl(dbSchema, name, columns)
  }

  /**
    * Generate DDL for raw (without Snowplow-specific columns and attributes) table
    *
    * @param dbSchema optional redshift schema name
    * @param name table name
    * @param columns list of generated DDLs for columns
    * @return full CREATE TABLE statement ready to be rendered
    */
  private[redshift] def getRawTableDdl(dbSchema: Option[String], name: String, columns: List[Column[RedShiftDdl]]): CreateTable[RedShiftDdl] = {
    val fullTableName = dbSchema.map(_ + "." + name).getOrElse(name)
    CreateTable(fullTableName, columns)
  }

  // Columns with data taken from self-describing schema
  protected[redshift] val selfDescSchemaColumns = List(
    Column[RedShiftDdl]("schema_vendor", SqlVarchar(128), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("schema_name", SqlVarchar(128), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("schema_format", SqlVarchar(128), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("schema_version", SqlVarchar(128), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull)))
  )

  // Snowplow-specific columns
  protected[redshift] val parentageColumns = List(
    Column[RedShiftDdl]("root_id", SqlChar(36), Set(CompressionEncoding(RawEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("root_tstamp", SqlTimestamp, Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("ref_root", SqlVarchar(255), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("ref_tree", SqlVarchar(1500), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull))),
    Column[RedShiftDdl]("ref_parent", SqlVarchar(255), Set(CompressionEncoding(ZstdEncoding)), Set(Nullability(NotNull)))
  )
  // List of compression encoding suggestions
  val encodingSuggestions: List[EncodingSuggestion] = List(lzoSuggestion, zstdSuggestion)

  /**
    * Generate DDL for atomic (with Snowplow-specific columns and attributes) table
    *
    * @param dbSchema optional redshift schema name
    * @param name     table name
    * @param columns  list of generated DDLs for columns
    * @return full CREATE TABLE statement ready to be rendered
    */
}
