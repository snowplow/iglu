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

import Dependencies._
import BuildSettings._

//Configure prompt to show current project
shellPrompt := { s => Project.extract(s).currentProject.id + " > " }

// Define our project with basic information and library dependencies
lazy val project = Project("iglu-server", file("."))
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      // Scala
      Libraries.akkaActor,
      Libraries.akkaSlf4j,
      Libraries.joda,
      Libraries.jodaTime,
      Libraries.json4s,
      Libraries.json4sScalaz,
      Libraries.jsonValidator,
      Libraries.slf4j,
      Libraries.slick,
      Libraries.slickpg,
      Libraries.slickpgJoda,
      Libraries.sprayCan,
      Libraries.sprayRouting,
      Libraries.swagger,
      // Scala (test only)
      Libraries.akkaTestKit,
      Libraries.specs2,
      Libraries.sprayTestKit
    )
  )
