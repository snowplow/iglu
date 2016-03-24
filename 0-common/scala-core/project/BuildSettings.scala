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

object BuildSettings {
  val igluCoreVersion = "0.1.0-M1"

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization       := "com.snowplowanalytics",
    version            := igluCoreVersion,
    description        := "Core entities for Iglu",
    scalaVersion       := "2.10.6",
    crossScalaVersions := Seq("2.10.6", "2.11.8"),
    scalacOptions      := Seq("-deprecation", "-encoding", "utf8", "-Yrangepos",
                              "-feature", "-unchecked", "-Xlog-reflective-calls",
                              "-Xlint"),
    resolvers     ++= Dependencies.resolutionRepos
  )

  // Publish settings
  // TODO: update with ivy credentials etc when we start using Nexus
  lazy val publishSettings = Seq[Setting[_]](

    crossPaths := false,
    publishTo <<= version { version =>
      val basePath = "target/repo/%s".format {
        if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases/"
      }
      Some(Resolver.file("Local Maven repository", file(basePath)) transactional())
    }
  )

  lazy val buildSettings = basicSettings ++ publishSettings
}
