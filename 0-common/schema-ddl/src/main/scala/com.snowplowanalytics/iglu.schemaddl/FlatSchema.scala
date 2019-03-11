package com.snowplowanalytics.iglu.schemaddl


import collection.immutable.ListMap

import cats.data.{NonEmptyList, State}

import io.circe.Json
import org.json4s.jackson.JsonMethods.compact

import com.snowplowanalytics.iglu.core.SelfDescribingData

import com.snowplowanalytics.iglu.schemaddl.VersionTree.VersionList
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{Pointer, Schema}
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Pointer.SchemaPointer
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.implicits._

import scala.annotation.tailrec

/**
  *
  * @param subschemas (order should not matter at this point)
  * @param required
  */
case class FlatSchema(subschemas: Set[(SchemaPointer, Schema)], required: Set[SchemaPointer]) {
  def add(pointer: SchemaPointer, schema: Schema): FlatSchema =
    FlatSchema(subschemas + (pointer -> schema), required)

  def withRequired(pointer: SchemaPointer, schema: Schema): FlatSchema = {
    val currentRequired = FlatSchema
      .getRequired(pointer, schema)
      .filter(pointer => FlatSchema.nestedRequired(required, pointer))
    this.copy(required = currentRequired ++ required)
  }

  def toMap: Map[SchemaPointer, Schema] = ListMap(subschemas.toList: _*)

  def show: String = subschemas
    .map { case (pointer, schema) => s"${pointer.show} -> ${compact(Schema.normalize(schema))}" }
    .mkString("\n")
}


object FlatSchema {
  def flatten(json: SelfDescribingData[Json]): Either[String, List[String]] = ???

  /** Schema group metadata extracted from repository */
  case class SchemaGroupMeta(vendor: String, name: String, versions: VersionList)
  /** Full schemas */
  case class SchemaGroup private(meta: SchemaGroupMeta, schemas: NonEmptyList[Schema])

  case class Changed(what: String, from: Schema.Primitive, to: Schema)
  case class Diff(added: (String, Schema), removed: List[String], changed: Changed)

  def diff(first: Schema, next: Schema): Diff = {
    ???
  }

  def flatten2(data: Json, schemas: SchemaGroup): Either[String, List[String]] = ???

  def build(schema: Schema): FlatSchema =
    Schema.traverse(schema, FlatSchema.save).runS(FlatSchema.empty).value

  /** Check if `current` JSON Pointer has all parent elements also required */
  // TODO: consider that pointer can have type: [X, null]
  @tailrec def nestedRequired(known: Set[SchemaPointer], current: SchemaPointer): Boolean =
    current.parent.flatMap(_.parent) match {
      case None | Some(Pointer.Root) => true  // Technically None should not be reached
      case Some(parent) => known.contains(parent) && nestedRequired(known, parent)
    }

  /** Redshift-specific */
  // TODO: type object with properties can be primitive if properties are empty
  def isPrimitive(schema: Schema): Boolean = {
    val isNested = schema.withType(CommonProperties.Type.Object) && schema.properties.isDefined
    !isNested
  }

  /** This property shouldn't have been added (FlattenerSpec.e10) */
  def hasPrimitiveParent(pointer: SchemaPointer): Boolean = {
    pointer.value.exists {
      case Pointer.Cursor.DownProperty(Pointer.SchemaProperty.Items) => true
      case _ => false
    }
  }

  def getRequired(cur: SchemaPointer, schema: Schema): Set[SchemaPointer] =
    schema
      .required.map(_.value.toSet)
      .getOrElse(Set.empty)
      .map(prop => cur.downProperty(Pointer.SchemaProperty.Properties).downField(prop))

  val empty = FlatSchema(Set.empty, Set.empty)

  def save(pointer: SchemaPointer, schema: Schema): State[FlatSchema, Unit] =
    State.modify[FlatSchema] { schemaTypes =>
      if (hasPrimitiveParent(pointer)) schemaTypes
      else if (isPrimitive(schema)) schemaTypes.add(pointer, schema).withRequired(pointer, schema)
      else schemaTypes.withRequired(pointer, schema)
    }
}