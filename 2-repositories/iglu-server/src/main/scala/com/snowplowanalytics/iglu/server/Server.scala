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

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import cats.data.Kleisli
import cats.syntax.functor._
import cats.syntax.apply._
import cats.effect.{ContextShift, ExitCode, IO, Timer }

import io.circe.syntax._

import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import fs2.Stream

import org.http4s.{ HttpApp, Response, Status, HttpRoutes, MediaType, Request, Headers }
import org.http4s.headers.`Content-Type`
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{ AutoSlash, CORS, CORSConfig, Logger, Timeout }
import org.http4s.syntax.string._

import org.http4s.rho.{ AuthedContext, RhoMiddleware }
import org.http4s.rho.bits.PathAST.{PathMatch, TypedPath}
import org.http4s.rho.swagger.syntax.{io => ioSwagger}
import org.http4s.rho.swagger.models.{ ApiKeyAuthDefinition, In, Info }

import doobie.implicits._
import doobie.util.transactor.Transactor

import com.snowplowanalytics.iglu.server.migrations.{ MigrateFrom, Bootstrap }
import com.snowplowanalytics.iglu.server.codecs.Swagger
import com.snowplowanalytics.iglu.server.model.{ Permission, IgluResponse }
import com.snowplowanalytics.iglu.server.storage.Storage
import com.snowplowanalytics.iglu.server.service._

import generated.BuildInfo.version

object Server {

  private val logger = Slf4jLogger.getLogger[IO]

  val ConnectionTimeout: FiniteDuration = 5.seconds

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
      apiInfo = Info(title = "Iglu Server API", version = version),
      basePath = Some(base),
      securityDefinitions = Map("Iglu API key" -> ApiKeyAuthDefinition("apikey", In.HEADER)),
      swaggerFormats = Swagger.Formats
    )

    base -> constructor(storage, PermissionContext, swagger)
  }

  def httpApp(storage: Storage[IO],
              debug: Boolean,
              patchesAllowed: Boolean,
              webhook: Webhook.WebhookClient[IO],
              ec: ExecutionContext)
             (implicit cs: ContextShift[IO], timer: Timer[IO]): HttpApp[IO] = {

    val services: List[(String, RoutesConstructor)] = List(
      "/api/meta"       -> MetaService.asRoutes(debug, patchesAllowed),
      "/api/schemas"    -> SchemaService.asRoutes(patchesAllowed, webhook),
      "/api/auth"       -> AuthService.asRoutes,
      "/api/validation" -> ValidationService.asRoutes,
      "/api/drafts"     -> DraftService.asRoutes,
    )

    val debugRoute = "/api/debug" -> DebugService.asRoutes(storage, ioSwagger.createRhoMiddleware())
    val staticRoute = "/static" -> StaticService.routes(ec)
    val routes = staticRoute :: services.map(addSwagger(storage))
    val corsConfig = CORSConfig(
      anyOrigin = true,
      anyMethod = false,
      allowedMethods = Some(Set("GET", "POST", "PUT", "OPTIONS", "DELETE")),
      allowedHeaders = Some(Set("content-type", "apikey")),
      allowCredentials = true,
      maxAge = 1.day.toSeconds)

    val serverRoutes = (if (debug) debugRoute :: routes else routes).map {
      case (endpoint, route) =>
        // Apply middleware
        val httpRoutes = BadRequestHandler(Timeout(ConnectionTimeout)(CORS(AutoSlash(route), corsConfig)))
        if (debug) {
          val redactHeadersWhen = (Headers.SensitiveHeaders + "apikey".ci).contains _
          (endpoint, Logger.httpRoutes[IO](true, true, redactHeadersWhen, Some(s => logger.info(s)))(httpRoutes))
        } else
          (endpoint, httpRoutes)
    }
    Kleisli[IO, Request[IO], Response[IO]](req => Router(serverRoutes: _*).run(req).getOrElse(NotFound))
  }


  def run(config: Config)(implicit ec: ExecutionContext, cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, ExitCode] = {
    for {
      _ <- Stream.eval(logger.info(s"Initializing server with following configuration: ${config.asJson.noSpaces}"))
      client <- BlazeClientBuilder[IO](ec).stream
      webhookClient = Webhook.WebhookClient(config.webhooks.getOrElse(Nil), client)
      storage <- Stream.resource(Storage.initialize[IO](ec)(config.database))
      builder = BlazeServerBuilder[IO]
        .bindHttp(config.repoServer.port, config.repoServer.interface)
        .withHttpApp(httpApp(storage, config.debug.getOrElse(false), config.patchesAllowed.getOrElse(false), webhookClient, ec))
        .withIdleTimeout(ConnectionTimeout)
      exit <- builder.serve
    } yield exit
  }


  def setup(config: Config, migrate: Option[MigrateFrom])(implicit cs: ContextShift[IO]) = {
    config.database match {
      case Config.StorageConfig.Postgres(host, port, dbname, username, password, driver, _, _) =>
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
