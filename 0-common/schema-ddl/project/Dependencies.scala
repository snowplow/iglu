/**
 * Copyright (c) 2014-2017 Snowplow Analytics Ltd. All rights reserved.
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

import sbt._

object Dependencies {
  object V {
    // Scala
    val igluCoreJson4s   = "0.3.0"
    val scalaz7          = "7.0.9"

    // Scala (test only)
    val specs2           = "4.0.1"
    val scalaCheck       = "1.13.5"
  }

  object Libraries {
    // Scala
    // Scala
    val igluCoreJson4s   = "com.snowplowanalytics"      %% "iglu-core-json4s"       % V.igluCoreJson4s
    val scalaz7          = "org.scalaz"                 %% "scalaz-core"            % V.scalaz7
    // Scala (test only)
    val specs2           = "org.specs2"                 %% "specs2-core"            % V.specs2     % "test"
    val scalaCheck       = "org.scalacheck"             %% "scalacheck"             % V.scalaCheck % "test"
  }
}
