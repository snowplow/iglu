/*
 * Copyright (c) 2016-2019 Snowplow Analytics Ltd. All rights reserved.
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

import annotation.tailrec
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

/**
  *
  * @param subschemas (order should not matter at this point)
  * @param required keys listed in `required` property, whose parents also listed in `required`
  *                 some of parent properties still can be `null` and thus not required
  * @param parents keys that are not primitive, but can contain important information (e.g. nullability)
  */
final case class FlatSchema(columns: Set[(SchemaPointer, Schema)],
                            required: Set[SchemaPointer],
                            parents: Set[(SchemaPointer, Schema)]) {

  def subschemas: Set[(SchemaPointer, Schema)] =
    for {
      (pointer, schema) <- columns
    } yield {
      if (nestedNullable(pointer)) (pointer, schema.copy(`type` = schema.`type`.map(_.withNull)))
      else (pointer, schema)
    }

  def withLeaf(pointer: SchemaPointer, schema: Schema): FlatSchema = {
    val updatedSchema =
      if (required.contains(pointer) || schema.canBeNull)
        schema.copy(`type` = schema.`type`.map(_.withNull))
      else schema
    FlatSchema(subschemas + (pointer -> updatedSchema), required, parents)
  }

  def withRequired(pointer: SchemaPointer, schema: Schema): FlatSchema = {
    val currentRequired = FlatSchema.getRequired(pointer, schema).filter(nestedRequired)
    this.copy(required = currentRequired ++ required)
  }

  // I think we added too much info to handle just nullability
  // I think instead we should mark all [X, null] and -no-required
  // i.e. if !nestedRequired - add null to type

  def withParent(pointer: SchemaPointer, schema: Schema): FlatSchema =
    FlatSchema(subschemas, required, parents + (pointer -> schema))

  /** All parents are required */
  @tailrec def nestedRequired(current: SchemaPointer): Boolean =
    current.parent.flatMap(_.parent) match {
      case None | Some(Pointer.Root) => true  // Technically None should not be reached
      case Some(parent) => required.contains(parent) && nestedRequired(parent)
    }

  /** Any parent properties contain `null` in `type` or `enum` */
  def nestedNullable(pointer: SchemaPointer): Boolean =
    parents
      .filter { case (p, _) => p.isParentOf(pointer) }
      .foldLeft(false) { case (acc, (_, schema)) =>
        schema.`type`.exists(_.nullable) || schema.enum.exists(_.value.contains(Json.Null)) || acc
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
  /** Redshift-specific */
  // TODO: type object with properties can be primitive if properties are empty
  def isLeaf(schema: Schema): Boolean = {
    val isNested = schema.withType(CommonProperties.Type.Object) && schema.properties.isDefined
    !isNested
  }

  /** This property shouldn't have been added (FlatSchemaSpec.e4) */
  def shouldBeIgnored(pointer: SchemaPointer): Boolean =
    pointer.value.exists {
      case Pointer.Cursor.DownProperty(Pointer.SchemaProperty.Items) => true
      case Pointer.Cursor.DownProperty(Pointer.SchemaProperty.PatternProperties) => true
      case _ => false
    }

  /** */
  def getRequired(cur: SchemaPointer, schema: Schema): Set[SchemaPointer] =
    schema
      .required.map(_.value.toSet)
      .getOrElse(Set.empty)
      .map(prop => cur.downProperty(Pointer.SchemaProperty.Properties).downField(prop))

  val empty = FlatSchema(Set.empty, Set.empty, Set.empty)

  def save(pointer: SchemaPointer, schema: Schema): State[FlatSchema, Unit] =
    State.modify[FlatSchema] { flatSchema =>
      if (shouldBeIgnored(pointer))
        flatSchema
      else if (isLeaf(schema))
        flatSchema
          .withRequired(pointer, schema)
          .withLeaf(pointer, schema)
        //
      else flatSchema
        .withRequired(pointer, schema)
        .withParent(pointer, schema)
    }
}