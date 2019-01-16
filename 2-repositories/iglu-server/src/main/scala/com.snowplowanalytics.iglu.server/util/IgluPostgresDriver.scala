/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server
package util

// Slick
import slick.driver.PostgresDriver
import com.github.tminglei.slickpg._
//import org.json4s.JValue

/**
 * Extension of the ``PostgresDriver`` to support json column and timestamp.
 */
object IgluPostgresDriver extends PostgresDriver with PgDateSupportJoda {

  override lazy val Implicit =
    new Implicits with DateTimeImplicits

  override val simple =
    new Implicits with SimpleQL with DateTimeImplicits
}
