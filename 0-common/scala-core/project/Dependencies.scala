/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
  val resolutionRepos = Seq(
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"  // For Specs2
  )

  object V {
    // Scala
    val json4s          = "3.2.11"
    val circe           = "0.4.1"
    val specs2          = "3.3.1"
  }

  object Libraries {
    val json4s           = "org.json4s"                 %% "json4s-jackson"            % V.json4s
    val circe            = "io.circe"                   %% "circe-core"                % V.circe
    val circeParser      = "io.circe"                   %% "circe-parser"              % V.circe

    // Scala (test only)
    val json4sTest       = "org.json4s"                 %% "json4s-jackson"            % V.json4s          % "test"
    val specs2           = "org.specs2"                 %% "specs2-core"               % V.specs2          % "test"
  }
}
