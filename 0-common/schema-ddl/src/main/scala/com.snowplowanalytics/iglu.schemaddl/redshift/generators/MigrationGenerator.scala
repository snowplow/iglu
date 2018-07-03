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

// This library
import com.snowplowanalytics.iglu.schemaddl.StringUtils._
import com.snowplowanalytics.iglu.schemaddl.sql.{AlterTable, NotNull, Nullability}
import com.snowplowanalytics.iglu.schemaddl.sql.generators.SqlMigrationGenerator
/**
 * Module containing all logic to generate DDL files with information required
 * to migration from one version of Schema to another
 */
object MigrationGenerator extends SqlMigrationGenerator {

  def buildAlterTable(tableName: String, varcharSize: Int)
                     (pair: (String, Map[String, String])): AlterTable =
    pair match {
      case (columnName, properties) =>
        val dataType = DdlGenerator.getDataType(properties, varcharSize, columnName)
        val encoding = DdlGenerator.getEncoding(properties, dataType, columnName)
        val nullable =
          if (DdlGenerator.checkNullability(properties, required = false)) None
          else Some(Nullability[RedShiftDdl](NotNull))
         AlterTable(tableName, AddColumn[RedShiftDdl](toSnakeCase(columnName), dataType, None, Some(encoding), nullable))
    }
}