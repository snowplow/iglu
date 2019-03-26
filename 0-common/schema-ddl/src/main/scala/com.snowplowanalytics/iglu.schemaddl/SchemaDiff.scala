package com.snowplowanalytics.iglu.schemaddl

import com.snowplowanalytics.iglu.schemaddl.Core.VersionPoint
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{Delta, Pointer, Schema}

/**
  * This class represents differences between *two* Schemas
  * Preserves order because list of 2+ schemas need to have consistent order of changes
  *
  * @param added list of properties sorted by their appearance in JSON Schemas
  * @param modified list of properties changed in target Schema;
  *                 if some property was added in successive Schema and modified
  *                 after that, it should appear in [[added]]
  * @param removed set of keys removed in target Schema
  */
case class SchemaDiff(added: Set[(Pointer.SchemaPointer, Schema)],
                      modified: Set[SchemaDiff.Modified],
                      removed: Set[(Pointer.SchemaPointer, Schema)])

object SchemaDiff {

  // TODO: migrations should be working on FinalDiff!!!

  /**
    * This version of `SchemaDiff` represents difference between two *or more* Schemas
    * hence preserves an order in which properties were added/removed
    */
  case class FinalDiff(added: List[(Pointer.SchemaPointer, Schema)],
                       modified: List[SchemaDiff.Modified],
                       removed: List[(Pointer.SchemaPointer, Schema)])

  // * At this point we should impose alphabetical/nullable order
  // * Removed should be applied from the end (e.g. [[a1, a2], [a3, r2]] should become [a1, a3])


  object FinalDiff {
    def empty: FinalDiff = FinalDiff(List.empty, List.empty, List.empty)

    // Should we have origin (1-0-0)?
    def build(diffs: List[SchemaDiff]): FinalDiff = {
      diffs.foldLeft(empty)
    }
  }

  val empty = SchemaDiff(Set.empty[(Pointer.SchemaPointer, Schema)], Set.empty, Set.empty)

  case class Modified(pointer: Pointer.SchemaPointer, from: Schema, to: Schema) {
    /** Show only properties that were changed */
    def getDelta = jsonschema.Delta.build(from, to)
  }

  // We should assume a property that if two particular schemas delta result in X pointer,
  // No two schemas between them can give pointer higher than X

  /**
    * Generate diff from source list of properties to target though sequence of intermediate
    *
    * @param source source list of JSON Schema properties
    * @param successive non-empty list of successive JSON Schema properties including target
    * @return diff between two Schmea
    */
  def diff(source: SubSchemas, target: SubSchemas): SchemaDiff = {
    val addedKeys = getAddedKeys(source, target)
    val modified = getModifiedProperties(source, target)
    val removedKeys = getRemovedProperties(source, target)
    SchemaDiff(addedKeys, modified, removedKeys)
  }

  /**
    * Get list of new properties in order they appear in subsequent Schemas
    *
    * @param source original Schema
    * @param successive all subsequent Schemas
    * @return possibly empty list of keys in correct order
    */
  def getAddedKeys(source: SubSchemas, successive: SubSchemas): Set[(Pointer.SchemaPointer, Schema)] = {
    val sourceKeys = source.map(_._1)
    successive.foldLeft(Set.empty[(Pointer.SchemaPointer, Schema)]) { case (acc, (pointer, schema)) =>
      if (sourceKeys.contains(pointer)) acc
      else acc + (pointer -> schema)
    }
  }

  type PointCheck = SchemaDiff => Boolean


  def becameRequired[A](getter: Delta => Delta.Changed[A])(m: Modified): Boolean =
    getter(m.getDelta) match {
      case d @ Delta.Changed(_, _) if d.nonEmpty =>
        val wasOptional = m.from.canBeNull
        val becameRequired = !m.to.canBeNull
        wasOptional && becameRequired
      case _ => false
    }

  /** New required property added or existing one became required */
  def required(diff: SchemaDiff): Boolean = {
    val newProperties = !diff.added.forall { case (_, schema) => schema.canBeNull }
    val becameRequiredType = diff.modified.exists(becameRequired(_.`type`))
    val becameRequiredEnum = diff.modified.exists(becameRequired(_.enum))
    newProperties || becameRequiredType || becameRequiredEnum
  }

  /** Changed or restricted type */
  def typeChange(diff: SchemaDiff): Boolean =
    diff.modified.exists { modified =>
      modified.getDelta.`type` match {
        case Delta.Changed(Some(from), Some(to)) => to.isSubsetOf(from) && from != to
        case Delta.Changed(None, Some(_)) => true
        case _ => false
      }
    }

  /** Revisioned type */
  def typeWidening(diff: SchemaDiff): Boolean =
    diff.modified.exists { modified =>
      modified.getDelta.`type` match {
        case Delta.Changed(Some(_), None) => true
        case Delta.Changed(Some(from), Some(to)) => from.isSubsetOf(to) && from != to
        case Delta.Changed(None, None) => false
        case Delta.Changed(None, Some(_)) => false
      }
    }

  /** Any constraints changed */
  def constraintWidening(diff: SchemaDiff): Boolean =
    diff.modified
      .map(_.getDelta)
      .exists { delta =>
        delta.multipleOf.nonEmpty ||
          delta.minimum.nonEmpty ||
          delta.maximum.nonEmpty ||
          delta.maxLength.nonEmpty ||
          delta.minLength.nonEmpty
      }

  def optionalAdded(diff: SchemaDiff): Boolean =
    diff.added.forall(_._2.canBeNull)

  val ModelChecks: List[PointCheck] =
    List(required, typeChange)

  val RevisionChecks: List[PointCheck] =
    List(typeWidening, constraintWidening)

  val AdditionChecks: List[PointCheck] =
    List(optionalAdded)

  def getPointer(diff: SchemaDiff): Option[VersionPoint] =
    if (ModelChecks.exists(p => p(diff))) Some(VersionPoint.Model)
    else if (RevisionChecks.exists(p => p(diff))) Some(VersionPoint.Revision)
    else if (AdditionChecks.exists(p => p(diff))) Some(VersionPoint.Addition)
    else None

  /**
    * Get list of JSON Schema properties modified between two versions
    *
    * @param source original list of JSON Schema properties
    * @param target final list of JSON Schema properties
    * @return set of properties changed in target Schema
    */
  def getModifiedProperties(source: SubSchemas, target: SubSchemas): Set[Modified] =
    target.flatMap { case (pointer, sourceSchema) =>
      source.find { case (p, _) => p == pointer } match {
        case None => Set.empty[Modified]
        case Some((_, targetSchema)) if sourceSchema == targetSchema => Set.empty[Modified]
        case Some((_, targetSchema)) => Set(Modified(pointer, sourceSchema, targetSchema))
      }
    }

  def getRemovedProperties(source: SubSchemas, target: SubSchemas): SubSchemas =
    source.foldLeft(Set.empty[(Pointer.SchemaPointer, Schema)]) {
      case (acc, (pointer, s)) =>
        val removed = !target.exists { case (p, _) => pointer == p }
        if (removed) acc + (pointer -> s) else acc
    }
}