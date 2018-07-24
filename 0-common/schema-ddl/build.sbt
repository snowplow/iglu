/**
 * Copyright (c) 2014-2017 Snowplow Analytics Ltd. All rights reserved.
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

lazy val dependencies = Seq(
  Dependencies.Libraries.igluCoreJson4s,
  Dependencies.Libraries.scalaz7,
  // Scala (test only)
  Dependencies.Libraries.specs2
)

lazy val basicSettings = Seq(
  organization          := "com.snowplowanalytics",
  name                  := "schema-ddl",
  version               := "0.8.0",
  description           := "Set of Abstract Syntax Trees for various DDL and Schema formats",
  scalaVersion          := "2.12.4",
  crossScalaVersions    :=  Seq("2.11.12", "2.12.4"),
  scalacOptions         := BuildSettings.compilerOptions,
  javacOptions          := BuildSettings.javaCompilerOptions,
  resolvers             += Resolver.jcenterRepo,
  shellPrompt           := { _ => "schema-ddl> " }
)

lazy val root = project.in(file("."))
  .settings(basicSettings ++ BuildSettings.buildSettings)
  .settings(libraryDependencies ++= dependencies)