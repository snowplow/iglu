/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
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

object BuildSettings {
  //Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization            := "com.snowplowanalytics",
    version                 := "0.2.0",
    description             := "Scala schema server for Iglu",
    scalaVersion            := "2.10.6",
    scalacOptions           := Seq("-deprecation", "-encoding", "utf8",
                               "-unchecked", "-feature", "-Xcheckinit"),
    scalacOptions in Test   := Seq("-Yrangepos"),
    maxErrors               := 5,
    // http://www.scala-sbt.org/0.13.0/docs/Detailed-Topics/Forking.html
    fork in run             := true,
    fork in Test            := true,
    // Ensure that the correct config file is loaded for testing
    javaOptions in Test     += "-Dconfig.file=./test.conf",
    resolvers               ++= Dependencies.resolutionRepos
  )

  // Makes our SBT app settings available from within the app
  lazy val scalifySettings = Seq(sourceGenerators in Compile <+=
    (sourceManaged in Compile, version, name, organization) map { 
      (d, v, n, o) =>
        val file = d / "settings.scala"
        IO.write(file, s"""
          |package com.snowplowanalytics.iglu.server.generated
          |object Settings {
          |  val organization = "$o"
          |  val version = "$v"
          |  val name = "$n"
          |  val shortName = "srp"
          |}
          |""".stripMargin)
        Seq(file)
    })

  // sbt-assembly settings for building an executable
  import sbtassembly.Plugin._
  import AssemblyKeys._
  
  lazy val sbtAssemblySettings = assemblySettings ++ Seq(
    // Simple name
    jarName in assembly := { s"${name.value}-${version.value}.jar" },
    test in assembly := {}
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++
    sbtAssemblySettings
}
