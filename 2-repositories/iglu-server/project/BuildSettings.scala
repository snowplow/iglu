/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0,
* and you may not use this file except in compliance with the
* Apache License Version 2.0.
* You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the Apache License Version 2.0 is distributed on
* an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.  See the Apache License Version 2.0 for the specific
* language governing permissions and limitations there under.
*/
import sbt._
import Keys._
import sbt.testing.TaskDef

object BuildSettings {
  //Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization            := "com.snowplowanalytics",
    version                 := "0.3.0",
    description             := "Scala schema server for Iglu",
    scalaVersion            := "2.11.12",
    scalacOptions           := Seq("-deprecation", "-encoding", "utf8",
                               "-unchecked", "-feature", "-Xcheckinit"),
    scalacOptions in Test   := Seq("-Yrangepos", "-deprecation"),
    maxErrors               := 5,
    // http://www.scala-sbt.org/0.13.0/docs/Detailed-Topics/Forking.html
    fork in run             := true,
    fork in Test            := true,
    // Ensure that the correct config file is loaded for testing
    javaOptions in Test     += "-Dconfig.file=./test.conf",
    resolvers               ++= Dependencies.resolutionRepos
  )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(sourceGenerators in Compile += task[Seq[File]] {
    val file = (sourceManaged in Compile).value / "settings.scala"
    IO.write(file, s"""
                      |package com.snowplowanalytics.iglu.server.generated
                      |object Settings {
                      |  val organization = "${organization.value}"
                      |  val version = "${version.value}"
                      |  val name = "${name.value}"
                      |  val shortName = "sr"
                      |}
                      |""".stripMargin)
    Seq(file)
  })

  // sbt-assembly settings for building an executable
  import sbtassembly.AssemblyKeys._
  import sbtassembly.AssemblyPlugin._

  lazy val sbtAssemblySettings = assemblySettings ++ Seq(
    // Simple name
    assemblyJarName in assembly := { s"${name.value}-${version.value}.jar" }
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++
    sbtAssemblySettings
}
