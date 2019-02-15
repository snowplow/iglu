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
package codecs

import scala.reflect.runtime.universe.TypeTag

import cats.Monad
import cats.syntax.either._

import eu.timepit.refined.types.numeric.NonNegInt

import org.http4s.{ Response, Status, Query }
import org.http4s.rho.bits._
import org.http4s.rho.bits.QueryParser.Params

import com.snowplowanalytics.iglu.core.SchemaVer
import com.snowplowanalytics.iglu.server.model.{DraftVersion, Schema}

trait UriParsers {

  case class LegacyBoolean(value: Boolean)

  implicit def schemaReprQueryParser[F[_]](implicit F: Monad[F]): QueryParser[F, Schema.Repr.Format] =
    new QueryParser[F, Schema.Repr.Format] {
      def collect(name: String, params: Params, default: Option[Schema.Repr.Format]): ResultResponse[F, Schema.Repr.Format] =
        params.get(name) match {
          case None =>
            SuccessResponse(default.getOrElse(Schema.Repr.Format.Uri))
          case Some(seq) =>
            seq.toList.map(Schema.Repr.Format.parse) match {
              case Nil => SuccessResponse(default.getOrElse(Schema.Repr.Format.Uri))
              case List(Some(repr)) => SuccessResponse(repr)
              case _ =>
                val response = Monad[F].pure(Response[F]().withStatus(Status.BadRequest))
                FailureResponse.pure[F](response)
            }
        }
    }

  def parseRepresentation[F[_]](query: Query)(implicit F: Monad[F]): ResultResponse[F, Schema.Repr.Format] = {
    val result = query.params.get("repr") match {
      case Some(s) =>
        Schema.Repr.Format.parse(s).toRight(s"Cannot recognize schema representation in query: $s")
      case None =>
        (query.params.getOrElse("metadata", "0"), query.params.getOrElse("body", "0")) match {
          case ("1", "1") => Schema.Repr.Format.Meta.asRight
          case ("0", "1") => Schema.Repr.Format.Canonical.asRight
          case ("1", "0") => Schema.Repr.Format.Meta.asRight
          case ("0", "0") => Schema.Repr.Format.Uri.asRight
          case (m, b) => s"Inconsistent metadata/body query parameters: $m/$b".asLeft
        }
    }

    result match {
      case Right(format) =>
        SuccessResponse[F, Schema.Repr.Format](format)
      case Left(error) =>
        val response = Response[F]()
          .withStatus(Status.BadRequest)
          .withBodyStream(Utils.toBytes(error))
        FailureResponse.pure[F](Monad[F].pure(response))
    }
  }

  implicit def schemaFormatStringParser[F[_]]: StringParser[F, Schema.Format] =
    new StringParser[F, Schema.Format] {
      override val typeTag: Some[TypeTag[Schema.Format]] = Some(implicitly[TypeTag[Schema.Format]])

      override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, Schema.Format] =
        Schema.Format.parse(s) match {
          case Some(format) => SuccessResponse(format)
          case None => FailureResponse.pure[F](BadRequest.pure(s"Unknown schema format: '$s'"))
        }
    }

  implicit def schemaVerParser[F[_]]: StringParser[F, SchemaVer.Full] =
    new StringParser[F, SchemaVer.Full] {
      override val typeTag: Some[TypeTag[SchemaVer.Full]] = Some(implicitly[TypeTag[SchemaVer.Full]])

      override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, SchemaVer.Full] =
        SchemaVer.parseFull(s) match {
          case Right(v) => SuccessResponse(v)
          case Left(e) => FailureResponse.pure[F](BadRequest.pure(s"Invalid boolean format: '$s', ${e.code}"))
        }
    }

  implicit def draftVersionParser[F[_]]: StringParser[F, DraftVersion] =
    new StringParser[F, DraftVersion] {
      override val typeTag: Some[TypeTag[DraftVersion]] = Some(implicitly[TypeTag[DraftVersion]])

      override def parse(s: String)(implicit F: Monad[F]): ResultResponse[F, DraftVersion] = {
        val int = try { Right(s.toInt) } catch { case _: NumberFormatException => Left(s"$s is not an integer") }
        int.flatMap(NonNegInt.from).fold(err => FailureResponse.pure[F](BadRequest.pure(err)), SuccessResponse.apply)
      }
    }
}

object UriParsers extends UriParsers
