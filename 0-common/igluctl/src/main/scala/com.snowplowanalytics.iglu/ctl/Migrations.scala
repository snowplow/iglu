/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.ctl

// scalaz
import scalaz._
import Scalaz._

// Iglu core
import com.snowplowanalytics.iglu.core.SchemaKey

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl._
import com.snowplowanalytics.iglu.schemaddl.redshift.generators.MigrationGenerator.generateMigration

// This library
import FileUtils.TextFile
import Utils.splitValidations

/**
 * Helper module for [[DdlCommand]] with functions responsible for DDL migrations
 */
object Migrations {
  /**
   * Transform whole Valid MigrationMap with different schemas, models and revisions
   * to flat list of [[TextFile]]'s with their relative path
   * and stringified DDL as content
   *
   * @param migrationMap migration map of all Schemas created with buildMigrationMap
   * @return flat list of [[TextFile]] ready to be written
   */
  def reifyMigrationMap(
    migrationMap: ValidMigrationMap,
    dbSchema: Option[String],
    varcharSize: Int): List[Validation[String, TextFile]] = {

    val validationFileList = migrationMap.map {
      case (source, Success(migrations)) => createTextFiles(migrations, source, varcharSize, dbSchema).success[String]
      case (source, Failure(error))      => error.failure
    }.toList
    val (migrationErrors, migrationFiles) = splitValidations(validationFileList)
    migrationFiles.flatten.map(_.success[String]) ++ migrationErrors.map(_.failure[TextFile])
  }

  /**
   * Helper function creating list of [[TextFile]] (with same source, varcharSize
   * and dbSchema) from list of migrations
   */
  def createTextFiles(migrations: List[Migration], source: SchemaKey, varcharSize: Int, dbSchema: Option[String]) = {
    val baseFiles = migrations.map { migration =>
      TextFile(migration.to.asString + ".sql", generateMigration(migration, varcharSize, dbSchema).render)
    }

    baseFiles
      .map(_.setBasePath(source.version.asString))
      .map(_.setBasePath(source.name))
      .map(_.setBasePath(source.vendor))
  }
}
