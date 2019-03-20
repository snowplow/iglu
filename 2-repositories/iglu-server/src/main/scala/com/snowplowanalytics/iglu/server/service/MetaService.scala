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
package com.snowplowanalytics.iglu.server.service

import cats.effect.{IO, Sync}
import cats.instances.int._
import cats.syntax.functor._
import cats.syntax.flatMap._

import io.circe._
import io.circe.generic.semiauto.deriveEncoder

import org.http4s.{ HttpRoutes, MediaType, Charset }
import org.http4s.headers.`Content-Type`
import org.http4s.rho.{AuthedContext, RhoMiddleware, RhoRoutes}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.swagger.syntax.{io => swaggerSyntax}

import com.snowplowanalytics.iglu.server.codecs.JsonCodecs._
import com.snowplowanalytics.iglu.server.generated.BuildInfo
import com.snowplowanalytics.iglu.server.model.Permission
import com.snowplowanalytics.iglu.server.storage.{ Storage, Postgres, InMemory }
import com.snowplowanalytics.iglu.server.middleware.PermissionMiddleware

class MetaService[F[+_]: Sync](debug: Boolean,
                               patchesAllowed: Boolean,
                               swagger: SwaggerSyntax[F],
                               ctx: AuthedContext[F, Permission],
                               db: Storage[F]) extends RhoRoutes[F] {
  import swagger._

  private val ok = Ok("OK").map(_.withContentType(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))

  "This route always responds with OK string" **
    GET / "health" |>> ok

  "This route responds with OK string if database is available" **
    GET / "health" / "db" |>> {
    for {
      _ <- db match {
        case pg: Postgres[F] => pg.ping.void
        case _ => Sync[F].unit
      }
      response <- ok
    } yield response
  }

  "This route responds with info about the Iglu Server" **
    GET / "server" >>> ctx.auth |>> { authInfo: Permission =>
    val database = db match {
      case _: Postgres[F] => "postgres"
      case _: InMemory[F] => "inmemory"
      case _ => "unknown"
    }
    for {
      count <- db.getSchemas.filter(s => authInfo.canRead(s.schemaMap.schemaKey.vendor)).as(1).compile.foldMonoid
      response <- Ok(MetaService.ServerInfo(BuildInfo.version, authInfo, database, count, debug, patchesAllowed))
    } yield response
  }
}

object MetaService {
  case class ServerInfo(version: String,
                        authInfo: Permission,
                        database: String,
                        schemaCount: Int,
                        debug: Boolean,
                        patchesAllowed: Boolean)

  implicit val serverInfoEncoderInstance: Encoder[ServerInfo] = deriveEncoder[ServerInfo]

  def asRoutes(debug: Boolean, patchesAllowed: Boolean)(db: Storage[IO], ctx: AuthedContext[IO, Permission], rhoMiddleware: RhoMiddleware[IO]): HttpRoutes[IO] = {
    val service = new MetaService(debug, patchesAllowed, swaggerSyntax, ctx, db).toRoutes(rhoMiddleware)
    PermissionMiddleware.wrapService(db, ctx, service)
  }
}
