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
import Keys._

object Dependencies {
  val resolutionRepos = Seq(
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"  // For Specs2
  )

  object V {
    // Scala
    val json4s          = "3.2.11"
    val circe           = "0.8.0"
    object specs2 {
      val _210          = "3.3.1"
      val _211          = "3.3.1"
      val _212          = "3.9.5"
    }
  }

  object Libraries {
    val json4s           = "org.json4s"                 %% "json4s-jackson"            % V.json4s
    val circe            = "io.circe"                   %% "circe-core"                % V.circe
    val circeParser      = "io.circe"                   %% "circe-parser"              % V.circe

    // Scala (test only)
    val json4sTest       = "org.json4s"                 %% "json4s-jackson"            % V.json4s          % "test"
    object specs2 {
      val _210           = "org.specs2"                 %% "specs2-core"               % V.specs2._210     % "test"
      val _211           = "org.specs2"                 %% "specs2-core"               % V.specs2._211     % "test"
      val _212           = "org.specs2"                 %% "specs2-core"               % V.specs2._212     % "test"
    }
  }

  def onVersion[A](all: Seq[A] = Seq(), on210: => Seq[A] = Seq(), on211: => Seq[A] = Seq(), on212: => Seq[A] = Seq()) =
    scalaVersion(v => all ++ (if (v.contains("2.10.")) {
      on210
    } else if (v.contains("2.11.")) {
      on211
    } else {
      on212
    }))
}
