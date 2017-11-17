/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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
    val igluCoreJson4s   = "0.2.0"
    val scalaz7          = "7.0.8"
    val json4s           = "3.3.0"
    // Scala (test only)
    val specs2           = "2.3.13"
    val scalazSpecs2     = "0.2"
    val scalaCheck       = "1.12.2"
  }

  object Libraries {
    // Scala
    val igluCoreJson4s   = "com.snowplowanalytics"      %% "iglu-core-json4s"          % V.igluCoreJson4s
    val scalaz7          = "org.scalaz"                 %% "scalaz-core"               % V.scalaz7
    val json4sJackson    = "org.json4s"                 %% "json4s-jackson"            % V.json4s
    // Scala (test only)
    val specs2           = "org.specs2"                 %% "specs2"                    % V.specs2         % "test"
    val scalazSpecs2     = "org.typelevel"              %% "scalaz-specs2"             % V.scalazSpecs2   % "test"
    val scalaCheck       = "org.scalacheck"             %% "scalacheck"                % V.scalaCheck     % "test"
  }
}
