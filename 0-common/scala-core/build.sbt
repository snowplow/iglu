/*
 * Copyright (c) 2017 Snowplow Analytics Ltd. All rights reserved.
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

import Dependencies._
import BuildSettings._
import Json4sBuildSettings._
import CirceBuildSettings._

shellPrompt in ThisBuild := { state => Project.extract(state).get(sbt.Keys.name) + " > " }

// Define our project, with basic project information and library dependencies
lazy val igluCore = (project in file("."))
  .settings(buildSettings: _*)
  .settings(
    name := "iglu-core",
    libraryDependencies ++= Dependencies.onVersion(
      all = Seq(
      // Scala (test only)
      Libraries.json4sTest),
      on210 = Seq(Libraries.specs2._210),
      on211 = Seq(Libraries.specs2._211),
      on212 = Seq(Libraries.specs2._212)
    ).value
  )

lazy val igluCoreJson4s = (project in file("iglu-core-json4s"))
  .dependsOn(igluCore)
  .settings(json4sBuildSettings: _*)
  .settings(
    name := "iglu-core-json4s",
    libraryDependencies ++= Dependencies.onVersion(
      all = Seq(
      // Scala
      Libraries.json4s),
      // Scala (test only)
      on210 = Seq(Libraries.specs2._210),
      on211 = Seq(Libraries.specs2._211),
      on212 = Seq(Libraries.specs2._212)
    ).value
  )

lazy val igluCoreCirce = (project in file("iglu-core-circe"))
  .dependsOn(igluCore)
  .settings(circeBuildSettings: _*)
  .settings(
    name := "iglu-core-circe",
    libraryDependencies ++= Dependencies.onVersion(
      all = Seq(
      // Scala
      Libraries.circe,
      Libraries.circeParser),
      // Scala (test only)
      on210 = Seq(Libraries.specs2._210),
      on211 = Seq(Libraries.specs2._211),
      on212 = Seq(Libraries.specs2._212)
    ).value
  )
