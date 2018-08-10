package com.snowplowanalytics.iglu.schemaddl.bigquery

import io.circe._

import cats.syntax.traverse._
import cats.syntax.validated._
import cats.instances.list._
import cats.data.{NonEmptyList, Validated, ValidatedNel}

import CastError._

sealed trait Row extends Product with Serializable

object Row {
  case object Null extends Row
  case class Primitive(value: Any) extends Row
  case class Repeated(values: List[Row]) extends Row
  case class Record(fields: List[(String, Row)]) extends Row

  /**
    * Turn JSON into BigQuery-compatible row, matching schema defined in `field`
    * Top-level function, called only one columns
    */
  def cast(field: Field)(value: Json): CastResult =
    field match {
      case Field(_, fieldType, Mode.Repeated) => castRepeated(fieldType)(value)
      case Field(_, fieldType, mode) => castValue(fieldType)(value).recover(mode)
    }

  /**
    * Turn primitive JSON or JSON object into BigQuery row
    * Does *not* handle arrays (as doesn't know about mode) and will turn them into string if meets
    */
  def castValue(fieldType: Type)(value: Json): CastResult = {
    fieldType match {
      case Type.String if value == Json.Null =>
        value.asString.fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.String =>   // Fallback strategy for union types
        value.asString.fold(Primitive(value.noSpaces))(Primitive(_)).validNel
      case Type.Boolean =>
        value.asBoolean.fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.Integer =>
        value.asNumber.flatMap(_.toLong).fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.Float =>
        value.asNumber.flatMap(_.toBigDecimal.map(_.bigDecimal)).fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.Timestamp =>
        value.asString.fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.Date =>
        value.asString.fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.DateTime =>
        value.asString.fold(WrongType(value, fieldType).invalidNel[Row])(Primitive(_).validNel)
      case Type.Record(subfields) =>
        value
          .asObject
          .fold(WrongType(value, fieldType).invalidNel[Map[String, Json]])(_.toMap.validNel)
          .andThen(castObject(subfields))
    }
  }

  private implicit class Recover(val value: CastResult) extends AnyVal {
    /** If cast failed, but value is null and column is nullable - fallback to null */
    def recover(mode: Mode): CastResult = value match {
      case Validated.Invalid(NonEmptyList(e @ WrongType(Json.Null, _), Nil)) =>
        if (mode == Mode.Nullable) Null.validNel else e.invalidNel
      case other => other
    }
    def eraseNull: Option[CastResult] = value match {
      case Validated.Invalid(NonEmptyList(WrongType(Json.Null, _), Nil)) => None
      case other => Some(other)
    }
  }

  /** Part of `castValue`, mapping JSON object into *ordered* list of `TableRow`s */
  def castObject(subfields: List[Field])(jsonObject: Map[String, Json]): CastResult = {
    val results = subfields.map {
      case f @ Field(name, fieldType, Mode.Repeated) =>
        jsonObject.get(name) match {
          case Some(json) => castRepeated(fieldType)(json).map { (f.normalName, _) }
          case None => (f.normalName, Row.Null).validNel[CastError]
        }
      case f @ Field(name, fieldType, Mode.Nullable) =>
        jsonObject.get(name) match {
          case Some(value) => castValue(fieldType)(value).recover(Mode.Nullable).map { (f.normalName, _) }
          case None => (f.normalName, Null).validNel
        }
      case f @ Field(name, fieldType, Mode.Required) =>
        jsonObject.get(name) match {
          case Some(value) => castValue(fieldType)(value).map { (f.normalName, _) }
          case None => MissingInValue(name, Json.fromFields(jsonObject)).invalidNel
        }
    }

    results
      .sequence[ValidatedNel[CastError, ?], (String, Row)]
      .map(Record.apply)
  }

  /** Try to cast JSON into a list of `fieldType`, fail if JSON is not an array */
  def castRepeated(fieldType: Type)(json: Json): CastResult =
    json.asArray match {
      case Some(values) => values
        .toList
        .flatMap(castValue(fieldType)(_).eraseNull)
        .sequence[ValidatedNel[CastError, ?], Row]
        .map(Repeated.apply)
      case None =>
        NotAnArray(json, fieldType).invalidNel
    }
}
