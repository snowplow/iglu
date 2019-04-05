/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.iglu.server
package storage

import java.util.UUID

import fs2.Stream

import cats.Monad
import cats.effect.{Clock, ContextShift, Effect}
import cats.implicits._
import cats.effect.{Sync, Resource}

import io.circe.Json

import doobie.hikari._
import doobie.util.ExecutionContexts

import com.snowplowanalytics.iglu.core.SchemaMap
import com.snowplowanalytics.iglu.server.Config.StorageConfig
import com.snowplowanalytics.iglu.server.model.{Permission, Schema, SchemaDraft}
import com.snowplowanalytics.iglu.server.model.SchemaDraft.DraftId

import scala.concurrent.ExecutionContext

trait Storage[F[_]] {

  def getSchema(schemaMap: SchemaMap)(implicit F: Monad[F]): F[Option[Schema]]
  def getSchemasByVendor(vendor: String, wildcard: Boolean)(implicit F: Monad[F]): Stream[F, Schema] =
    if (wildcard) getSchemas.filter(_.schemaMap.schemaKey.vendor.startsWith(vendor))
    else getSchemas.filter(_.schemaMap.schemaKey.vendor === vendor )
  def getSchemasByVendorName(vendor: String, name: String)(implicit F: Monad[F]): Stream[F, Schema] =
    getSchemasByVendor(vendor, false).filter(_.schemaMap.schemaKey.name === name)
  def getSchemas(implicit F: Monad[F]): Stream[F, Schema]
  def getSchemaBody(schemaMap: SchemaMap)(implicit F: Monad[F]): F[Option[Json]] =
    getSchema(schemaMap).nested.map(_.body).value
  def addSchema(schemaMap: SchemaMap, body: Json, isPublic: Boolean)(implicit C: Clock[F], M: Monad[F]): F[Unit]
  def updateSchema(schemaMap: SchemaMap, body: Json, isPublic: Boolean)(implicit C: Clock[F], M: Monad[F]): F[Unit]

  def addDraft(draftId: DraftId, body: Json, isPublic: Boolean)(implicit C: Clock[F], M: Monad[F]): F[Unit]
  def getDraft(draftId: DraftId)(implicit F: Monad[F]): F[Option[SchemaDraft]]
  def getDrafts(implicit F: Monad[F]): Stream[F, SchemaDraft]

  def getPermission(apiKey: UUID)(implicit F: Monad[F]): F[Option[Permission]]
  def addPermission(uuid: UUID, permission: Permission)(implicit F: Monad[F]): F[Unit]
  def addKeyPair(keyPair: Permission.KeyPair, vendor: Permission.Vendor)(implicit F: Monad[F]): F[Unit] =
    for {
      _ <- addPermission(keyPair.read, Permission.ReadOnlyAny.copy(vendor = vendor))
      _ <- addPermission(keyPair.write, Permission.Write.copy(vendor = vendor))
    } yield ()
  def deletePermission(uuid: UUID)(implicit F: Monad[F]): F[Unit]
}

object Storage {

  /** Storage returned an object that cannot be parsed */
  case class IncompatibleStorage(message: String) extends Throwable {
    override def getMessage: String = message
  }

  /**
    *
    * @param transactEC a second ExecutionContext for executing JDBC operations
    * @param config
    * @tparam F
    * @return
    */
  def initialize[F[_]: Effect: ContextShift](transactEC: ExecutionContext)
                                            (config: StorageConfig): Resource[F, Storage[F]] = {
    config match {
      case StorageConfig.Dummy =>
        Resource.liftF(storage.InMemory.empty)
      case StorageConfig.Postgres(host, port, name, username, password, driver, threads, maxPoolSize) =>
        val url = s"jdbc:postgresql://$host:$port/$name"
        for {
          connectEC <- ExecutionContexts.fixedThreadPool(threads.getOrElse(32))
          xa <- HikariTransactor.newHikariTransactor[F](driver, url, username, password, connectEC, transactEC)
          _ <- Resource.liftF {
            xa.configure { ds =>
              Sync[F].delay {
                ds.setMaximumPoolSize(maxPoolSize.getOrElse(10))
              }
            }
          }
          storage <- Resource.liftF(Postgres(xa).ping)
        } yield storage
    }
  }
}
