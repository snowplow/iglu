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
