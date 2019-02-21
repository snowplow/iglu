package com.snowplowanalytics.iglu.server

import cats.Applicative
import cats.implicits._
import com.snowplowanalytics.iglu.core.SchemaKey
import io.circe.{Encoder, Json}
import org.http4s.{Request, Uri}
import org.http4s.client.Client

sealed trait Webhook

object Webhook {
  case class SchemaPublished(uri: Uri, vendorPrefixes: Option[List[String]]) extends Webhook
  case class ApiKeyPublished(uri: Uri, vendorPrefixes: Option[List[String]]) extends Webhook

  case class WebhookClient[F[_]](webhooks: List[Webhook], httpClient: Client[F]) {
    def schemaPublished(schemaKey: SchemaKey, updated: Boolean)(implicit F: Applicative[F]): F[Unit] =
      webhooks.traverse_ {
        case SchemaPublished(uri, _) =>
          val event = SchemaPublishedEvent(schemaKey, updated)
          val req = Request[F]().withUri(uri).withBodyStream(Utils.toBytes(event))
          httpClient.fetch(req) { _ => F.pure(()) }

        case ApiKeyPublished(_, _) => F.pure(())
      }

    def apiKeyPublished: F[Unit] = ???
  }

  case class SchemaPublishedEvent(schemaKey: SchemaKey, updated: Boolean)

  implicit val schemaPublishedEventEncoder: Encoder[SchemaPublishedEvent] =
    Encoder.instance { event =>
      Json.fromFields(List(
        "schemaKey" -> Json.fromString(event.schemaKey.toSchemaUri),
        "updated" -> Json.fromBoolean(event.updated)
      ))
    }
}

