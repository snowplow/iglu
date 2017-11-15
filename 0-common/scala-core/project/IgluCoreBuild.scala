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

object IgluCoreBuild extends Build {

  import Dependencies._
  import BuildSettings._
  import Json4sBuildSettings._
  import CirceBuildSettings._

  // Configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // Define our project, with basic project information and library dependencies
  lazy val project = Project("iglu-core", file("."))
    .settings(buildSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        // Scala (test only)
        Libraries.json4sTest,
        Libraries.specs2
      )
    )

  lazy val igluCoreJson4s = Project("iglu-core-json4s", file("iglu-core-json4s"))
    .settings(json4sBuildSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        // Scala
        Libraries.json4s,
        // Scala (test only)
        Libraries.specs2
      )
    )
    .dependsOn(project)

  lazy val igluCoreCirce = Project("iglu-core-circe", file("iglu-core-circe"))
    .settings(circeBuildSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        // Scala
        Libraries.circe,
        Libraries.circeParser,
        // Scala (test only)
        Libraries.specs2
      )
    )
    .dependsOn(project)

}
