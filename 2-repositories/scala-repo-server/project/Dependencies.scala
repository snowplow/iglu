/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
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
import sbt._

object Dependencies {
  val resolutionRepos = Seq(
    "Spray repo"            at "http://repo.spray.io",
    "Typesafe repository"   at "http://repo.typesafe.com/typesafe/releases"
  )

  object V {
    // Scala
    val akka      = "2.2.4"
    val spray     = "1.3.1"
    val storehaus = "0.9.0"

    // Scala (test only)
    val specs2    = "2.3.12"
  }

  object Libraries {
    // Scala
    val akkaActor     = "com.typesafe.akka" %% "akka-actor"             % V.akka
    val sprayCan      = "io.spray"          % "spray-can"               % V.spray
    val sprayRouting  = "io.spray"          % "spray-routing"           % V.spray
    val storehausCore = "com.twitter"       % "storehaus-core_2.10"     % V.storehaus
    val storehausDDB  = "com.twitter"       % "storehaus-dynamodb_2.10" % V.storehaus

    // Scala (test only)
    val specs2        = "org.specs2"        %% "specs2-core"            % V.specs2 % "test"
    val sprayTestKit  = "io.spray"          % "spray-testkit"           % V.spray  % "test"
  }
}
