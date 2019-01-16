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

// Define our project with basic information and library dependencies
lazy val root = project.in(file("."))
  .settings(BuildSettings.buildSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      // Scala
      Dependencies.Libraries.scopt,
      Dependencies.Libraries.schemaDdl,
      Dependencies.Libraries.akkaActor,
      Dependencies.Libraries.akkaSlf4j,
      Dependencies.Libraries.joda,
      Dependencies.Libraries.jodaTime,
      Dependencies.Libraries.json4s,
      Dependencies.Libraries.jsonValidator,
      Dependencies.Libraries.slf4j,
      Dependencies.Libraries.slick,
      Dependencies.Libraries.slickpg,
      Dependencies.Libraries.slickpgJoda,
      Dependencies.Libraries.akkaHttp,
      Dependencies.Libraries.swaggerAkkaHttp,
      // Scala (test only)
      Dependencies.Libraries.akkaTestKit,
      Dependencies.Libraries.specs2,
      Dependencies.Libraries.akkaHttpTestKit
    )
  )
  .enablePlugins(JavaAppPackaging)
