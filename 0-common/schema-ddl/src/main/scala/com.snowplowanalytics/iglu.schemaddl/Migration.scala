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

// Scala
import scala.collection.immutable.ListMap

// Scalaz
import scalaz._
import Scalaz._

// Iglu core
import com.snowplowanalytics.iglu.core.{ SchemaKey, SchemaVer }

// This project
import FlatSchema.flattenJsonSchema

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
case class Migration(
  vendor: String,
  name: String,
  from: SchemaVer,
  to: SchemaVer,
  diff: Migration.SchemaDiff)

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

  implicit val schemaOrdering = implicitly[Order[Int]].contramap[IgluSchema](_.self.version.addition)

  /**
   * This class represents differences between two Schemas
   *
   * Nothing except [[added]] is used now
   *
   * @param added list of properties sorted by their appearance in JSON Schemas
   * @param modified list of properties changed in target Schema;
   *                 if some property was added in successive Schema and modified
   *                 after that, it should appear in [[added]]
   * @param removed set of keys removed in target Schema
   */
  case class SchemaDiff(added: PropertyList, modified: PropertyList, removed: Set[String])

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
   * @return migraion object with data about source, target and diff
   */
  def buildMigration(sourceSchema: IgluSchema, successiveSchemas: List[IgluSchema]): Validation[String, Migration] = {
    val flatSource = flattenJsonSchema(sourceSchema.schema, splitProduct = false).map(_.elems)
    val flatSuccessive = successiveSchemas.map(s => flattenJsonSchema(s.schema, splitProduct = false)).sequenceU
    val target = successiveSchemas.last

    (flatSource |@| flatSuccessive) { (source, successive) =>
      val diff = diffMaps(source, successive.map(_.elems))
      Migration(
        sourceSchema.self.vendor,
        sourceSchema.self.name,
        sourceSchema.self.version,
        target.self.version, diff)
    }
  }

  /**
   * Generate diff from source list of properties to target though sequence of intermediate
   *
   * @param source source list of JSON Schema properties
   * @param successive non-empty list of successive JSON Schema properties including target
   * @return diff between two Schmea
   */
  def diffMaps(source: PropertyList, successive: List[PropertyList]): SchemaDiff = {
    val target = successive.last
    val addedKeys = getAddedKeys(source, successive)
    val added = getSubmap(addedKeys, target)
    val modified = getModifiedProperties(source, target, addedKeys)
    val removedKeys = (source.keys.toList diff target.keys.toList).toSet
    SchemaDiff(added, modified, removedKeys)
  }

  /**
   * Get list of new properties in order they appear in subsequent Schemas
   *
   * @param source original Schema
   * @param successive all subsequent Schemas
   * @return possibly empty list of keys in correct order
   */
  def getAddedKeys(source: PropertyList, successive: List[PropertyList]): List[String] = {
    val (newKeys, _) = successive.foldLeft((List.empty[String], source)) { case ((acc, previous), current) =>
      (acc ++ (current.keys.toList diff previous.keys.toList), current)
    }
    newKeys
  }

  /**
   * Get list of JSON Schema properties modified between two versions
   *
   * @param source original list of JSON Schema properties
   * @param target final list of JSON Schema properties
   * @param addedKeys keys to be excluded from this diff, added properties
   *                  should be included in [[SchemaDiff]] separately
   * @return list of properties changed in target Schema
   */
  def getModifiedProperties(source: PropertyList, target: PropertyList, addedKeys: List[String]): PropertyList = {
    val targetModified = target.filterKeys(!addedKeys.contains(_))
    ListMap(targetModified.toList diff source.toList: _*)
  }

  /**
   * Get submap by keys
   *
   * @param keys ordered list of submap keys
   * @param original original Map
   * @return sorted Map of new properties
   */
  def getSubmap[K, V](keys: List[K], original: Map[K, V]): ListMap[K, V] =
    ListMap(keys.flatMap(k => original.get(k).map((k, _))): _*)

  /**
   * Map each single Schema to List of subsequent Schemas
   * 1-0-0 -> [[1-0-1], [1-0-1, 1-0-2], [1-0-1, 1-0-2, 1-0-3]]
   * 1-0-1 -> [[1-0-2], [1-0-2, 1-0-3]]
   * 1-0-2 -> [[1-0-3]]
   *
   * @param schemas list of self-describing schemas
   * @return list of pairs of schema and its targets
   */
  def mapSchemasToTargets(schemas: List[IgluSchema]): List[(IgluSchema, List[List[IgluSchema]])] = {
    val sortedSchemas = schemas.sorted(schemaOrdering.toScalaOrdering)
    for {
      current <- sortedSchemas
      (_, to) = sortedSchemas.span(_ <= current)
      if to.nonEmpty
    } yield (current, initSegments(to))
  }

  /**
   * Return list of non-empty initial segments of sequence
   * [1,2,3,4] -> [[1,2,3,4], [1,2,3], [1,2], [1]]
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
  def getOrdering(migrationMap: ValidMigrationMap): Map[RevisionGroup, Validation[String, List[String]]] =
    migrationMap.filterKeys(_.version.addition == 0).map {
      case (description, Success(migrations)) =>
        val longestMigration = migrations.map(_.diff.added.keys.toList).maxBy(x => x.length)
        (revisionCriterion(description), longestMigration.success)
      case (description, Failure(message)) =>
        (revisionCriterion(description), message.failure)
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
      .mapValues(_.sequenceU)
      .asInstanceOf[ValidMigrationMap]  // Help IDE to infer type
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
   * @param schemaKey Schema description
   * @return tuple of vendor, name, model, revision
   */
  private def revisionCriterion(schemaKey: SchemaKey): RevisionGroup =
    (schemaKey.vendor, schemaKey.name, schemaKey.version.model, schemaKey.version.revision)
}