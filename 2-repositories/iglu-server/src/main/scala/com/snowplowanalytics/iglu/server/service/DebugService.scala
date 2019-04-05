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

import cats.effect.{ IO, Sync }
import cats.implicits._

import org.http4s.rho.{ RhoRoutes, RhoMiddleware }
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.rho.swagger.syntax.{io => swaggerSyntax}


import com.snowplowanalytics.iglu.server.storage.{ Storage, InMemory }

/** Service showing whole in-memory state. Use for development only */
class DebugService[F[_]: Sync](swagger: SwaggerSyntax[F], db: Storage[F]) extends RhoRoutes[F] {
  import swagger._

  "Show internal state" **
    GET |>> {
    db match {
      case InMemory(ref) =>
        for {
          db <- ref.get
          response <- Ok(db.toString)
        } yield response
      case other => NotImplemented(s"Cannot show $other")
    }
  }
}

object DebugService {
  def asRoutes(db: Storage[IO], rhoMiddleware: RhoMiddleware[IO]) =
    new DebugService(swaggerSyntax, db).toRoutes(rhoMiddleware)
}
