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

import Dependencies._
import BuildSettings._

lazy val root = project.in(file("."))
  .settings(
    name                  :=  "igluctl",
    organization          :=  "com.snowplowanalytics",
    version               :=  "0.6.0-rc1",
    description           :=  "Iglu Command Line Interface",
    scalaVersion          :=  "2.11.12"
  )
  .settings(buildSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      // Java
      Libraries.jsonValidator,
      Libraries.awsJava,
      // JAXB APIs
      Libraries.javaxXmlBind,
      Libraries.jaxbCore,
      Libraries.jaxbImpl,
      Libraries.activation,
      // Scala
      Libraries.schemaddl,
      Libraries.igluClient,
      Libraries.scopt,
      Libraries.scalajHttp,
      Libraries.awscala,
      // Scala (test only)
      Libraries.specs2,
      Libraries.scalaCheck
    )
  )
