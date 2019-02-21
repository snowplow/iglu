package com.snowplowanalytics.iglu.server

import cats.Applicative
import cats.implicits._

import io.circe.{Encoder, Json}

import org.http4s.{Request, Response, Status, Uri}
import org.http4s.client.Client

import com.snowplowanalytics.iglu.core.SchemaKey

sealed trait Webhook

object Webhook {

  case class SchemaPublished(uri: Uri, vendorPrefixes: Option[List[String]]) extends Webhook

  case class WebhookClient[F[_]](webhooks: List[Webhook], httpClient: Client[F]) {
    def schemaPublished(schemaKey: SchemaKey, updated: Boolean)(implicit F: Applicative[F]): F[List[Either[String, Unit]]] =
      webhooks.traverse {
        case SchemaPublished(uri, prefixes) if prefixes.isEmpty || prefixes.getOrElse(List()).exists(schemaKey.vendor.startsWith(_)) =>
          val event = SchemaPublishedEvent(schemaKey, updated)
          val req = Request[F]().withUri(uri).withBodyStream(Utils.toBytes(event))
          httpClient.fetch(req) { res: Response[F] =>
            res.status match {
              case Status(code) if code != 200 => F.pure(code.toString.asLeft)
              case _ => F.pure(().asRight)
            }
          }
        case _ => F.pure(().asRight)
      }
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

