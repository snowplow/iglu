/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.AssemblyPlugin.defaultShellScript
import sbt._
import Keys._


object BuildSettings {

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    scalacOptions         :=  Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-Ywarn-unused-import",
      "-Ywarn-nullary-unit",
      "-Xfatal-warnings",
      "-Xlint",
      "-language:higherKinds",
      "-Ypartial-unification",
      "-Xfuture"),
    scalacOptions in (Compile, console) := Seq(
      "-deprecation",
      "-encoding", "UTF-8"
    ),
    scalacOptions in (Compile, doc) ++= Seq(
      "-no-link-warnings" // Suppresses problems with Scaladoc @throws links
    ),
    javacOptions := Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),
    scalacOptions in Test := Seq("-Yrangepos")
  )

  lazy val scalifySettings = Seq(
    sourceGenerators in Compile += Def.task {
      val file = (sourceManaged in Compile).value / "settings.scala"
      IO.write(file, """package com.snowplowanalytics.iglu.ctl.generated
                       |object ProjectSettings {
                       |  val version = "%s"
                       |  val name = "%s"
                       |}
                       |""".stripMargin.format(version.value, name.value, organization.value, scalaVersion.value))
      Seq(file)
    }.taskValue
  )

  // Assembly settings
  lazy val sbtAssemblySettings: Seq[Setting[_]] = Seq(

    // Executable jarfile
    assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) },

    // Name it as an executable
    assemblyJarName in assembly := { name.value },

    // Make this executable
    mainClass in assembly := Some("com.snowplowanalytics.iglu.ctl.Main")
  )

  lazy val buildSettings = basicSettings ++ scalifySettings ++ sbtAssemblySettings
}
