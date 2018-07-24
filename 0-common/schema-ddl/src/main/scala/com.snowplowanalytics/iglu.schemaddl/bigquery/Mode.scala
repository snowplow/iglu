package com.snowplowanalytics.iglu.schemaddl.bigquery

sealed trait Mode extends Product with Serializable

object Mode {
  case object Nullable extends Mode
  case object Required extends Mode
  case object Repeated extends Mode

  def required(indeed: Boolean): Mode =
    if (indeed) Required else Nullable

  def sort(fieldMode: Mode): Int =
    fieldMode match {
      case Required => -1
      case Repeated => 0
      case Nullable => 1
    }
}
