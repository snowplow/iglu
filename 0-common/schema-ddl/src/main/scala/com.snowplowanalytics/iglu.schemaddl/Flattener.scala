package com.snowplowanalytics.iglu.schemaddl


import cats.data.NonEmptyList
import io.circe.Json
import com.snowplowanalytics.iglu.core.{SelfDescribingData, SelfDescribingSchema}
import com.snowplowanalytics.iglu.schemaddl.VersionTree.VersionList
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema

//object Flattener {
//  def flatten(json: SelfDescribingData[Json]): Either[String, List[String]] = ???
//
//
//  case class SchemaGroupMeta(vendor: String, name: String, versions: VersionList)
//  case class SchemaGroup private(meta: SchemaGroupMeta, schemas: NonEmptyList[Schema])
//
//  case class Changed(what: String, from: PrimitiveSchema, to: Schema)
//  case class Diff(added: (String, Schema), removed: List[String], changed: Changed)
//
//  def diff(first: Schema, next: Schema): Diff = {
//
//    ???
//  }
//
//  def flatten2(data: Json, schemas: SchemaGroup): Either[String, List[String]] = ???
//}
