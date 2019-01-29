package com.snowplowanalytics.iglu.server.codecs

import scala.reflect.runtime.universe.TypeTag

import cats.Monad

import eu.timepit.refined.types.numeric.NonNegInt

import org.http4s.rho.bits.{FailureResponse, ResultResponse, StringParser, SuccessResponse}

import com.snowplowanalytics.iglu.core.SchemaVer
import com.snowplowanalytics.iglu.server.model.{DraftVersion, Schema}

trait UriParsers {

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
