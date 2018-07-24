package com.snowplowanalytics.iglu.schemaddl.bigquery

sealed trait Type extends Product with Serializable

object Type {
  case object String extends Type
  case object Boolean extends Type
  case object Bytes extends Type
  case object Integer extends Type
  case object Float extends Type
  case object Date extends Type
  case object DateTime extends Type
  case object Timestamp extends Type
  case class Record(fields: List[Field]) extends Type
}

