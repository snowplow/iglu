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
    val akka  = "2.3.4"
    val joda = "1.6"
    val jodaMapper = "1.1.0"
    val jodaTime = "2.3"
    val postgres = "9.1-901-1.jdbc4"
    val slf4j = "1.6.4"
    val slick = "2.0.2"
    val spray = "1.3.1"
    val sprayj = "1.2.5"

    // Scala (test only)
    val specs2    = "2.3.13"
  }

  object Libraries {
    // Scala
    val akkaActor     = "com.typesafe.akka"    %% "akka-actor"        % V.akka
    val akkaSlf4j     = "com.typesafe.akka"    %% "akka-slf4j"        % V.akka
    val joda          = "org.joda"             %  "joda-convert"      % V.joda
    val jodaMapper    = "com.github.tototoshi" %% "slick-joda-mapper" % V.jodaMapper
    val jodaTime      = "joda-time"            %  "joda-time"         % V.jodaTime
    val postgres      = "postgresql"           %  "postgresql"        % V.postgres
    val slick         = "com.typesafe.slick"   %% "slick"             % V.slick
    val slf4j         = "org.slf4j"            %  "slf4j-nop"         % V.slf4j
    val sprayCan      = "io.spray"             %  "spray-can"         % V.spray
    val sprayJson     = "io.spray"             %% "spray-json"        % V.sprayj
    val sprayRouting  = "io.spray"             %  "spray-routing"     % V.spray

    // Scala (test only)
    val specs2        = "org.specs2"         %% "specs2-core"  % V.specs2 % "test"
    val sprayTestKit  = "io.spray"           %  "spray-testkit" % V.spray  % "test"
  }
}
