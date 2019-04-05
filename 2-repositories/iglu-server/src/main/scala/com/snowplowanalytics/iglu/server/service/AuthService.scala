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
package service

import java.util.UUID

import cats.implicits._
import cats.data.EitherT
import cats.effect.{ IO, Sync }

import io.circe._
import io.circe.syntax._

import org.http4s._
import org.http4s.circe._
import org.http4s.rho.{RhoMiddleware, RhoRoutes}
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.AuthedContext
import org.http4s.rho.swagger.syntax.{io => swaggerSyntax}

import com.snowplowanalytics.iglu.server.middleware.PermissionMiddleware
import com.snowplowanalytics.iglu.server.codecs.JsonCodecs._
import com.snowplowanalytics.iglu.server.model.Permission
import com.snowplowanalytics.iglu.server.model.IgluResponse
import com.snowplowanalytics.iglu.server.storage.Storage


class AuthService[F[+_]: Sync](swagger: SwaggerSyntax[F], ctx: AuthedContext[F, Permission], db: Storage[F])
  extends RhoRoutes[F] {
  import swagger._
  import AuthService._

  val apikey = paramD[UUID]("key", "UUID apikey to delete")

  "Route to delete api key" **
    DELETE / "keygen" +? apikey >>> ctx.auth |>> deleteKey _

  "Route to generate new keys" **
    POST / "keygen" >>> ctx.auth |>> { (req: Request[F], authInfo: Permission) =>
    if (authInfo.key.contains(Permission.KeyAction.Create))
      for {
        vendorE  <- req.attemptAs[UrlForm]
          .subflatMap(vendorFromForm)
          .recoverWith { case MalformedMessageBodyFailure(_, None) => vendorFromBody(req) }
          .value
        response <- vendorE match {
          case Right(vendor) if authInfo.canCreatePermission(vendor.asString) =>
            for {
              keyPair    <- Permission.KeyPair.generate[F]
              _          <- db.addKeyPair(keyPair, vendor)
              okResponse <- Ok(keyPair.asJson)
            } yield okResponse
          case Right(vendor) =>
            Forbidden(IgluResponse.Message(s"Cannot create ${vendor.show} using your permissions"): IgluResponse)
          case Left(error) =>
            BadRequest(IgluResponse.Message(s"Cannot decode vendorPrefix: ${error.message}"): IgluResponse)
        }
      } yield response
    else Forbidden(IgluResponse.Message("Not sufficient privileges to create keys"): IgluResponse)
  }


  def deleteKey(key: UUID, permission: Permission) =
    if (permission.key.contains(Permission.KeyAction.Delete)) {
      db.deletePermission(key) *> Ok(IgluResponse.Message(s"Keys have been deleted"): IgluResponse)
    } else Forbidden("Not sufficient privileges to delete key")
}

object AuthService {

  case class GenerateKey(vendorPrefix: Permission.Vendor)

  implicit val schemaGenerateReq: Decoder[GenerateKey] =
    Decoder.instance { cursor =>
      cursor
        .downField("vendorPrefix")
        .as[String]
        .map(Permission.Vendor.parse)
        .map(GenerateKey.apply)
    }

  def asRoutes(db: Storage[IO], ctx: AuthedContext[IO, Permission], rhoMiddleware: RhoMiddleware[IO]): HttpRoutes[IO] = {
    val service = new AuthService(swaggerSyntax, ctx, db).toRoutes(rhoMiddleware)
    PermissionMiddleware.wrapService(db, ctx, service)
  }

  def vendorFromForm(urlForm: UrlForm): Either[DecodeFailure, Permission.Vendor] =
    urlForm
      .getFirst("vendor_prefix")
      .map(Permission.Vendor.parse)
      .toRight(InvalidMessageBodyFailure(s"Cannot extract vendor_prefix from ${UrlForm.encodeString(Charset.`UTF-8`)(urlForm)}"))

  def vendorFromBody[F[_]: Sync](request: Request[F]) = {
    request.attemptAs[Json].flatMap { json =>
      EitherT.fromEither[F](json.as[GenerateKey].fold(
        e => InvalidMessageBodyFailure(e.show).asLeft[Permission.Vendor],
        p => p.vendorPrefix.asRight[DecodeFailure])
      )
    }
  }
}
