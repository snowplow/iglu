package com.snowplowanalytics.iglu.schemaddl.bigquery

import io.circe._

import cats.data.NonEmptyList

sealed trait CastError extends Product with Serializable

object CastError {
  case class WrongType(value: Json, expected: Type) extends CastError
  /** Field should be repeatable, but value is not an JSON Array */
  case class NotAnArray(value: Json, expected: Type) extends CastError
  /** Value is required by Schema, but missing in JSON object */
  case class MissingInValue(key: String, value: Json) extends CastError
}

