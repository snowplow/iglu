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

import scala.concurrent.ExecutionContext

import cats.data.Kleisli
import cats.syntax.functor._
import cats.syntax.apply._
import cats.effect.{ContextShift, ExitCode, IO, Timer }

import fs2.Stream

import org.http4s.{ HttpApp, Response, Status, HttpRoutes, MediaType, Request }
import org.http4s.headers.`Content-Type`
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.AutoSlash

import org.http4s.rho.{ AuthedContext, RhoMiddleware }
import org.http4s.rho.bits.PathAST.{PathMatch, TypedPath}
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import org.http4s.rho.swagger.models.{ApiKeyAuthDefinition, In }

import doobie.implicits._
import doobie.util.transactor.Transactor

import com.snowplowanalytics.iglu.server.migrations.{ MigrateFrom, Bootstrap }
import com.snowplowanalytics.iglu.server.codecs.Swagger
import com.snowplowanalytics.iglu.server.model.{ Permission, IgluResponse }
import com.snowplowanalytics.iglu.server.storage.Storage
import com.snowplowanalytics.iglu.server.service._

object Server {

  type RoutesConstructor = (Storage[IO], AuthedContext[IO, Permission], RhoMiddleware[IO]) => HttpRoutes[IO]

  val PermissionContext: AuthedContext[IO, Permission] =
    new AuthedContext[IO, Permission]

  /** Default server's response if endpoint was not found */
  val NotFound: Response[IO] =
    Response[IO]()
      .withStatus(Status.NotFound)
      .withBodyStream(Utils.toBytes(IgluResponse.EndpointNotFound: IgluResponse))
      .withContentType(`Content-Type`(MediaType.application.json))

  def addSwagger(storage: Storage[IO])(service: (String, RoutesConstructor)) = {
    val (base, constructor) = service
    val swagger = ioSwagger.createRhoMiddleware(
      apiPath = TypedPath(PathMatch("swagger.json")),
      basePath = Some(base),
      securityDefinitions = Map("Iglu API key" -> ApiKeyAuthDefinition("apikey", In.HEADER)),
      swaggerFormats = Swagger.Formats
    )

    base -> constructor(storage, PermissionContext, swagger)
  }

  def httpApp(storage: Storage[IO],
              debug: Boolean,
              patchesAllowed: Boolean,
              webhooks: List[Config.Webhook],
              ec: ExecutionContext)
             (implicit cs: ContextShift[IO], timer: Timer[IO]): HttpApp[IO] = {

    val services: List[(String, RoutesConstructor)] = List(
      "/api/meta"       -> MetaService.asRoutes,
      "/api/schemas"    -> SchemaService.asRoutes(patchesAllowed, webhooks),
      "/api/auth"       -> AuthService.asRoutes,
      "/api/validation" -> ValidationService.asRoutes,
      "/api/drafts"     -> DraftService.asRoutes,
    )

    val debugRoute = "/api/debug" -> DebugService.asRoutes(storage, ioSwagger.createRhoMiddleware())
    val staticRoute = "/static" -> StaticService.routes(ec)
    val routes = staticRoute :: services.map(addSwagger(storage))
    val serverRoutes = (if (debug) debugRoute :: routes else routes).map {
      case (endpoint, route) => (endpoint, AutoSlash(route))
    }
    Kleisli[IO, Request[IO], Response[IO]](req => Router(serverRoutes: _*).run(req).getOrElse(NotFound))
  }

  def run(config: Config)(implicit ec: ExecutionContext, cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, ExitCode] =
    for {
      storage <- Stream.resource(Storage.initialize[IO](ec)(config.database))
      builder = BlazeServerBuilder[IO]
        .bindHttp(config.repoServer.port, config.repoServer.interface)
        .withHttpApp(httpApp(storage, config.debug.getOrElse(false), config.patchesAllowed.getOrElse(false), config.webhooks.getOrElse(List()), ec))
      stream <- builder.serve
    } yield stream

  def setup(config: Config, migrate: Option[MigrateFrom])(implicit cs: ContextShift[IO]) = {
    config.database match {
      case Config.StorageConfig.Postgres(host, port, dbname, username, password, driver, _) =>
        val url = s"jdbc:postgresql://$host:$port/$dbname"
        val xa = Transactor.fromDriverManager[IO](driver, url, username, password)
        val action = migrate match {
          case Some(migration) =>
            migration.perform.transact(xa) *>
              IO(println(s"All tables were migrated in $dbname from $migration"))
          case None =>
            Bootstrap.initialize[IO](xa) *>
              IO(println(s"Tables were initialized in $dbname"))
        }
        action.as(ExitCode.Success)
      case Config.StorageConfig.Dummy =>
        IO(println(s"Nothing to setup with dummy storage")).as(ExitCode.Error)
    }
  }
}
