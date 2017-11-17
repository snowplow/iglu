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
import bintray.{BintrayIvyResolver, BintrayRepo, BintrayCredentials}
import bintray.BintrayPlugin._
import bintray.BintrayKeys._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.AssemblyPlugin.defaultShellScript
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging.autoImport._
import com.typesafe.sbt.packager.SettingsHelper._
import sbt._
import Keys._


object BuildSettings {

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    name                  :=  "igluctl",
    organization          :=  "com.snowplowanalytics",
    version               :=  "0.3.0",
    description           :=  "Iglu Command Line Interface",
    scalaVersion          :=  "2.11.8",
    crossScalaVersions    :=  Seq("2.10.6", "2.11.8"),
    scalacOptions         :=  Seq("-deprecation", "-encoding", "utf8",
                                  "-unchecked", "-feature",
                                  "-target:jvm-1.7"),
    scalacOptions in Test :=  Seq("-Yrangepos")
  )

  lazy val scalifySettings = Seq(sourceGenerators in Compile <+= (sourceManaged in Compile, version, name, organization, scalaVersion) map { (d, v, n, o, sv) =>
    val file = d / "settings.scala"
    IO.write(file, """package com.snowplowanalytics.iglu.ctl.generated
                     |object ProjectSettings {
                     |  val version = "%s"
                     |  val name = "%s"
                     |}
                     |""".stripMargin.format(v, n))
    Seq(file)
  })

  // Bintray publish settings
  lazy val publishSettings = bintraySettings ++ Seq[Setting[_]](
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    bintrayOrganization := Some("snowplow"),
    bintrayRepository := "snowplow-generic",
    publishMavenStyle := false,

    // Custom Bintray resolver used to publish package with custom Ivy patterns (custom path in Bintray)
    // It requires ~/.bintray/credentials file and bintrayOrganization setting
    publishTo in bintray := {
      for {
        bintrayOrg     <- bintrayOrganization.value
        credentials    <- BintrayCredentials.read(bintrayCredentialsFile.value).right.toOption.flatten
        bintrayRepo     = BintrayRepo(credentials, Some(bintrayOrg), name.value)
        connectedRepo   = bintrayRepo.client.repo(bintrayOrg, bintrayRepository.value)
        bintrayPackage  = connectedRepo.get(name.value)
        ivyResolver     = BintrayIvyResolver(
          bintrayRepository.value,
          bintrayPackage.version(version.value),
          Seq(s"${name.value}_${version.value.replace('-', '_')}.[ext]"),   // Ivy Pattern
          release = true)
      } yield new RawRepository(ivyResolver)
    }
  )

  // Assembly settings
  lazy val sbtAssemblySettings: Seq[Setting[_]] = Seq(

    // Executable jarfile
    assemblyOption in assembly ~= { _.copy(prependShellScript = Some(defaultShellScript)) },

    // Name it as an executable
    assemblyJarName in assembly := { s"${name.value}" },

    // Make this executable
    mainClass in assembly := Some("com.snowplowanalytics.iglu.ctl.Main")
  )

  // Packaging (sbt-native-packager) settings
  lazy val deploySettings = Seq(
    // Don't publish MD5/SHA checksums
    checksums := Nil,

    // Assemble zip archive with fat jar
    mappings in Universal := {
      // Use fat jar built by sbt-assembly
      val fatJar = (assembly in Compile).value

      // We don't need anything except fat jar
      val nativePackagerFiles = Nil

      // Add the fat jar
      nativePackagerFiles :+ (fatJar -> ("/" + fatJar.getName))
    },

    scriptClasspath := Seq((assemblyJarName in assembly).value)

  ) ++ makeDeploymentSettings(Universal, packageBin in Universal, "zip")

  lazy val buildSettings = basicSettings ++ scalifySettings ++ publishSettings ++ sbtAssemblySettings ++ deploySettings
}
