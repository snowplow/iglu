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

import org.http4s._
import org.http4s.client.Client

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}
import com.snowplowanalytics.iglu.server.Webhook.WebhookClient

class WebhookSpec extends org.specs2.Specification { def is = s2"""
  Return Unit results for successful requests    $e1
  Return status code results for failed requests $e2
  """

  def e1 = {
    val response = WebhookSpec.webhookClient.schemaPublished(SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,0)), true)
    response.unsafeRunSync() mustEqual List(().asRight, ().asRight)
  }

  def e2 = {
    val response = WebhookSpec.badWebhookClient.schemaPublished(SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,0)), true)
    response.unsafeRunSync() mustEqual List("502".asLeft, "502".asLeft)
  }
}

object WebhookSpec {
  val webhooks = List(
    Webhook.SchemaPublished(Uri.uri("https://example.com/endpoint"), None),
    Webhook.SchemaPublished(Uri.uri("https://example2.com/endpoint"), Some(List("com", "org.acme", "org.snowplow")))
  )

  val client: Client[IO] = Client.fromHttpApp(HttpApp[IO](r => Response[IO]().withEntity(r.body).pure[IO]))
  val badClient: Client[IO] = Client.fromHttpApp(HttpApp[IO](r => Response[IO]().withStatus(Status.BadGateway).withEntity(r.body).pure[IO]))

  val webhookClient = WebhookClient(webhooks, client)
  val badWebhookClient = WebhookClient(webhooks, badClient)
}
