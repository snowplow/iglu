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
package middleware

import java.util.UUID

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._

import org.http4s.{ Request, HttpRoutes, Response, Status }
import org.http4s.server.AuthMiddleware
import org.http4s.util.CaseInsensitiveString
import org.http4s.rho.AuthedContext

import com.snowplowanalytics.iglu.server.model.{ Permission, IgluResponse }
import com.snowplowanalytics.iglu.server.storage.Storage


/**
  * Used only in HTTP Services, where all endpoints require authentication
  *
  * For any *failed* attempt attempt of authentication, e.g. if apikey is provided,
  * but not found - return 404, "Schema not found" in order to hide the fact
  * of schema existence
  */
object PermissionMiddleware {

  val ApiKey = "apikey"

  /** Build an authentication middleware on top of storage */
  def apply[F[_]: Sync](storage: Storage[F]): AuthMiddleware[F, Permission] =
    AuthMiddleware.noSpider(Kleisli { request => auth[F](storage)(request) }, badRequestHandler)  // TODO: SchemaServiceSpec.e6

  /** Extract API key from HTTP request */
  def getApiKey[F[_]](request: Request[F]): Option[Either[Throwable, UUID]] =
    request.headers.get(CaseInsensitiveString(ApiKey))
      .map { header => header.value }
      .map { apiKey => Either.catchOnly[IllegalArgumentException](UUID.fromString(apiKey)) }

  def wrapService[F[_]: Sync](db: Storage[F], ctx: AuthedContext[F, Permission], service: HttpRoutes[F]): HttpRoutes[F] =
    PermissionMiddleware[F](db).apply(ctx.toService(service))

  private val notFoundBody = Utils.toBytes(IgluResponse.SchemaNotFound: IgluResponse)

  /** Authenticate request against storage */
  private def auth[F[_]: Sync](storage: Storage[F])(request: Request[F]): OptionT[F, Permission] = {
    getApiKey(request) match {
      case Some(Right(apiKey)) =>
        OptionT(apiKey.pure[F].flatMap(storage.getPermission))
      case None =>
        OptionT.pure(Permission.Noop)
      case Some(_) =>
        OptionT.none
    }
  }

  /** Handle invalid apikey as BadRequest, everything else as NotFound
    * (because we don't reveal presence of private resources)
    */
  private def badRequestHandler[F[_]](implicit F: Applicative[F]): Request[F] => F[Response[F]] =
    s => getApiKey(s) match {
      case Some(Left(error)) =>
        val body = Utils.toBytes[F, IgluResponse](IgluResponse.Message(s"Error parsing apikey HTTP header. ${error.getMessage}"))
        F.pure(Response[F](Status.BadRequest, body = body))
      case _ => // TODO: relax this behavior to exist only for /schemas/ven/name/format/ver
        F.pure(Response[F](Status.NotFound, body = notFoundBody))
    }
}
