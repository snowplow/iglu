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
import bintray.BintrayPlugin._
import bintray.BintrayKeys._
import sbt._
import Keys._

object BuildSettings {

  // Basic settings common for Iglu project and all its subprojects
  lazy val commonSettings = Seq[Setting[_]](
    organization       := "com.snowplowanalytics",
    version            := "0.2.0",
    scalaVersion       := "2.12.4",
    crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.4"),
    scalacOptions      := Seq("-deprecation", "-encoding", "utf8", "-Yrangepos",
                              "-feature", "-unchecked", "-Xlog-reflective-calls",
                              "-Xlint"),
    resolvers     ++= Dependencies.resolutionRepos
  )

  // Basic settings only for Iglu Core
  lazy val igluCoreBuildSettings = commonSettings ++ Seq[Setting[_]](
    description        := "Core entities for Iglu"
  )

  // Publish settings
  lazy val publishSettings = bintraySettings ++ Seq[Setting[_]](
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    bintrayOrganization := Some("snowplow"),
    bintrayRepository := "snowplow-maven"
  )

  // Maven Central publishing settings
  lazy val mavenCentralExtras = Seq[Setting[_]](
    pomIncludeRepository := { x => false },
    homepage := Some(url("http://snowplowanalytics.com")),
    scmInfo := Some(ScmInfo(url("https://github.com/snowplow/iglu"), "scm:git@github.com:snowplow/iglu.git")),
    pomExtra := (
      <developers>
        <developer>
          <name>Snowplow Analytics Ltd</name>
          <email>support@snowplowanalytics.com</email>
          <organization>Snowplow Analytics Ltd</organization>
          <organizationUrl>http://snowplowanalytics.com</organizationUrl>
        </developer>
      </developers>)
  )

  lazy val buildSettings = igluCoreBuildSettings ++ publishSettings ++ mavenCentralExtras
}
