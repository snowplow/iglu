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

import cats.implicits._
import cats.effect.IO

import io.circe.Json

import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.rho.swagger.syntax.io.createRhoMiddleware

import com.snowplowanalytics.iglu.server.service.SchemaService

class ServerSpec extends org.specs2.Specification { def is = s2"""
  Return 200 for public schema $e1
  Return empty JSON object for public schema $e2
  """

  def e1 = {
    val response = ServerSpec.request(Request(Method.GET, Uri.uri("/com.acme/event/jsonschema/1-0-0")))
    response.unsafeRunSync().status must beEqualTo(Status.Ok)
  }

  def e2 = {
    val response = ServerSpec.request(Request(Method.GET, Uri.uri("/com.acme/event/jsonschema/1-0-0")))
    response.flatMap(_.as[Json]).unsafeRunSync() must beEqualTo(SpecHelpers.selfSchemaZero)
  }
}

object ServerSpec {
  val middleware = createRhoMiddleware()

  val client: Client[IO] = Client.fromHttpApp(HttpApp[IO](r => Response[IO]().withEntity(r.body).pure[IO]))

  def request(req: Request[IO]): IO[Response[IO]] = {
    for {
      storage <- storage.InMemory.get[IO](SpecHelpers.exampleState)
      service <- SchemaService.asRoutes(false, Webhook.WebhookClient(List(), client))(storage, SpecHelpers.ctx, middleware).run(req).value
    } yield service.getOrElse(Response(Status.NotFound))
  }
}
