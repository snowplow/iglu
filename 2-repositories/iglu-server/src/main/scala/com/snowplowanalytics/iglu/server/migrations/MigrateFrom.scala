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
package com.snowplowanalytics.iglu.server.migrations

import doobie.free.connection.ConnectionIO

sealed trait MigrateFrom {
  def perform: ConnectionIO[Unit]
}

object MigrateFrom {
  case object `0.5.0` extends MigrateFrom {
    def perform: ConnectionIO[Unit] =
      Fifth.perform
  }

  def parse(s: String): Option[MigrateFrom] = s match {
    case "0.5.0" => Some(`0.5.0`)
    case _ => None
  }
}