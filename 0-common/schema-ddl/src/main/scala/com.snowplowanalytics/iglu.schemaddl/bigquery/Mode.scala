/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.iglu.schemaddl.bigquery

/** BigQuery SQL mode */
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
