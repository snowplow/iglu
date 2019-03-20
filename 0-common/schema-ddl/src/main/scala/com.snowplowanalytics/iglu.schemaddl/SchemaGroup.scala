package com.snowplowanalytics.iglu.schemaddl

import cats.Monad
import cats.data.{EitherT, NonEmptyList}
import cats.implicits._

import com.snowplowanalytics.iglu.core.{SchemaKey, SelfDescribingSchema}
import com.snowplowanalytics.iglu.schemaddl.VersionTree.VersionList
import com.snowplowanalytics.iglu.schemaddl.jsonschema.Schema

import Core._
import SchemaGroup._

/** Full schemas */
case class SchemaGroup private(meta: Meta, schemas: NonEmptyList[Schema]) {
  /**
    *
    * @param schema
    * @param ignoreRules
    * @return
    */
  def add(schema: SelfDescribingSchema[Schema], ignoreRules: Boolean): Either[String, SchemaGroup] = {
    val diff = schemas.last
    SchemaDiff.becameRequired()
    SchemaDiff.getPointer(schema.schema)

    ???
  }
}

object SchemaGroup {

  type FetchList[F[_]] = (String, String) => F[SchemaList]
  type FetchSchema[F[_]] = SchemaKey => F[Schema]

  def build[F[_]: Monad](fetchList: FetchList[F], fetch: FetchSchema[F])
                        (schemaKey: SchemaKey): EitherT[F, VersionTree.BuildingError, SchemaGroup] =
    for {
      list <- EitherT.liftF(fetchList(schemaKey.vendor, schemaKey.name))
      meta <- EitherT.fromEither[F](Meta.build(list))
      schemas <- EitherT.liftF(meta.versions.versions.traverse[F, Schema](ver => fetch(schemaKey.copy(version = ver))))
    } yield SchemaGroup(meta, schemas)

  /** Schema group metadata extracted from repository */
  case class Meta(vendor: String, name: String, versions: VersionList)

  object Meta {
    def build(schemas: SchemaList): Either[VersionTree.BuildingError, Meta] =
      for {
        tree <- VersionTree.build(schemas.schemas.map(_.version))
        head = schemas.schemas.head
      } yield Meta(head.vendor, head.name, tree.versionList)
  }
}
