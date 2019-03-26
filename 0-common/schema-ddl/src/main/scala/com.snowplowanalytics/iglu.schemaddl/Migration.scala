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

// cats
import cats.Order
import cats.data._
import cats.implicits._

// Iglu core
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Pointer, Schema }

// Iglu core
import com.snowplowanalytics.iglu.core.{ SchemaMap, SchemaVer }


/**
 * Class representing common information about Schema change, without details
 * about specific DDLs
 *
 * @param vendor Schema vendor
 * @param name Schema name
 * @param from source Schema version
 * @param to target Schema version
 * @param diff ordered map of added Schema properties
 */
case class Migration(vendor: String, name: String, from: SchemaVer.Full, to: SchemaVer.Full, diff: SchemaDiff)

// When our Migrations will be precise enough, so they could handle
// number size, varchar size, null etc we could implement build of DDLs
// with simple fold:
//
// migrations.foldLeft(List(initialDdl)) { (ddls: List[DDL], cur: Migration) =>
//   applyMigration(ddls.head, cur) :: ddls
// }
//
// But now we need to generate DDLs relying only on their Schemas and use
// migrations to synchronize column order

object Migration {

  // The pointer always need to be decided based on initial 1-0-0 schema

  implicit val schemaOrdering = implicitly[Order[Int]].contramap[IgluSchema](_.self.schemaKey.version.addition)

  /**
   * Map schemas by their Schema Criterion m-r-*
   * If any field except ADDITION differs, two schemas are unrelated
   * Examples:
   * com.acme/event/1-0-*  -> 1-0-0, 1-0-1, 1-0-2
   * com.acme/config/1-0-* -> 1-0-0, 1-0-1, 1-0-2
   * com.acme/config/1-1-* -> 1-1-0, 1-1-1
   *
   * @param schemas list of schemas to be distincted
   * @return map of schemas grouped by their common REVISION
   */
  def distinctSchemas(schemas: List[IgluSchema]): Map[RevisionGroup, List[IgluSchema]] =
    schemas.groupBy(s => revisionCriterion(s.self))

  /**
   * Build migration from a `sourceSchema` to the last schema in list of `successiveSchemas`
   * This method requires all intermediate schemas because we need to keep an order of properties
   *
   * @param sourceSchema schema from which we need to generate migration
   * @param successiveSchemas list of schemas, though which we need to generate migration,
   *                          with destination in the end of list
   * @return migration object with data about source, target and diff
   */
  def buildMigration(sourceSchema: IgluSchema, successiveSchemas: List[IgluSchema]): Migration = {
    val source = FlatSchema.build(sourceSchema.schema).subschemas
    val successive = successiveSchemas.map(s => FlatSchema.build(s.schema))
    val target = successiveSchemas.last

    Migration(
      sourceSchema.self.schemaKey.vendor,
      sourceSchema.self.schemaKey.name,
      sourceSchema.self.schemaKey.version,
      target.self.schemaKey.version,
      SchemaDiff.diff(source, ???))
  }

  /**
   * Map each single Schema to List of subsequent Schemas
   * 1-0-0 -> List([1-0-1], [1-0-1, 1-0-2], [1-0-1, 1-0-2, 1-0-3])
   * 1-0-1 -> List([1-0-2], [1-0-2, 1-0-3])
   * 1-0-2 -> List([1-0-3])
   *
   * @param schemas list of self-describing schemas
   * @return list of pairs of schema and its targets
   */
  def mapSchemasToTargets(schemas: List[IgluSchema]): List[(IgluSchema, List[List[IgluSchema]])] = {
    val sortedSchemas = schemas.sorted(schemaOrdering.toOrdering)
    for {
      current <- sortedSchemas
      (_, to) = sortedSchemas.span(_ <= current)
      if to.nonEmpty
    } yield (current, initSegments(to))
  }

  /**
   * Return list of non-empty initial segments of sequence
   * [1,2,3,4] -> List([1,2,3,4], [1,2,3], [1,2], [1])
   *
   * @param xs original sequence
   * @return list of non-empty initial segments ordered by descendence
   */
  def initSegments[A](xs: List[A]): List[List[A]] = {
    val reversed = xs.reverse
    def go(ys: List[A]): List[List[A]] = ys match {
      case Nil       => Nil
      case _ :: Nil  => Nil
      case _ :: tail => tail.reverse :: go(tail)
    }
    xs :: go(reversed)
  }

  /**
   * Get ordering for subsequent properties
   * This will not include columns from 1-0-0, but only added in
   * subsequent Schema additions
   *
   * @param migrationMap map of each Schema to list of all available migrations
   * @return map of revision criterion to list with all added columns
   */
  def getOrdering(migrationMap: ValidMigrationMap): Map[RevisionGroup, Validated[String, Set[Pointer.SchemaPointer]]] =
    migrationMap.filterKeys(_.schemaKey.version.addition == 0).map {
      case (description, migrations) =>
        val longestMigration = migrations.map(_.diff.added.map(_._1)).maxBy(x => x.size)
        (revisionCriterion(description), longestMigration.valid)
    }

  /**
   * Build [[ValidMigrationMap]], a map of source Schema to it's migrations,
   * where all source Schemas belong to a single model-revision Schema criterion
   *
   * @param schemas source Schemas belong to a single model-revision criterion
   * @return migration map of each Schema to list of all available migrations
   */
  def buildAdditionMigrations(schemas: List[IgluSchema]): ValidMigrationMap = {
    val migrations = for {
      (source, targetsSequence) <- mapSchemasToTargets(schemas)
      targets                   <- targetsSequence
    } yield (source.self, buildMigration(source, targets))

    migrations.groupBy(_._1)
      .mapValues(_.map(_._2))
  }

  /**
   * Map all Schemas (sources) to all its targets through all its migration path
   * Then build all migrations of sources to targets through its migration path
   *
   * @param allSchemas list of found Schemas, probably with different
   *                   names, models and revisions
   * @return migration map of each Schema to list of all available migrations
   */
  def buildMigrationMap(allSchemas: List[IgluSchema]): ValidMigrationMap = {
    // We need groupedMigrationMap to ensure that buildAdditionMigrations
    // and mapSchemasToTargets receive lists of Schema belonging to a single revision
    val groupedMigrationMap = distinctSchemas(allSchemas).map {
      case (revision, schemas) => (revision, buildAdditionMigrations(schemas))
    }
    groupedMigrationMap.flatMap { case (_, additionMigrationMap) =>
      additionMigrationMap.map { case (source, migrations) => (source, migrations) }
    }
  }

  /**
   * Extract tuple of four Schema attributes to group Schemas by revision
   *
   * @param schemaMap Schema description
   * @return tuple of vendor, name, model, revision
   */
  private def revisionCriterion(schemaMap: SchemaMap): RevisionGroup =
    (schemaMap.schemaKey.vendor, schemaMap.schemaKey.name, schemaMap.schemaKey.version.model, schemaMap.schemaKey.version.revision)
}
