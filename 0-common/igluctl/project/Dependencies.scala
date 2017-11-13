/*
 * Copyright (c) 2016-2017 Snowplow Analytics Ltd. All rights reserved.
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

object Dependencies {

  object V {
    // Java
    val jsonValidator    = "2.2.6"
    val awsJava          = "1.11.250"
    // Scala
    val schemaddl        = "0.6.0"
    val scopt            = "3.5.0"
    val scalajHttp       = "2.3.0"
    val awscala          = "0.5.9"
    // Scala (test only)
    val specs2           = "4.0.1"
    val scalaCheck       = "1.13.5"
  }


  object Libraries {
    // Java
    val jsonValidator    = "com.github.fge"             %  "json-schema-validator"     % V.jsonValidator
    val awsJava          = "com.amazonaws"              %  "aws-java-sdk-s3"           % V.awsJava
    // Scala
    val schemaddl        = "com.snowplowanalytics"      %% "schema-ddl"                % V.schemaddl
    val scopt            = "com.github.scopt"           %% "scopt"                     % V.scopt
    val scalajHttp       = "org.scalaj"                 %% "scalaj-http"               % V.scalajHttp
    val awscala          = "com.github.seratch"         %% "awscala"                   % V.awscala
    // Scala (test only)
    val specs2           = "org.specs2"                 %% "specs2-core"               % V.specs2         % "test"
    val scalaCheck       = "org.scalacheck"             %% "scalacheck"                % V.scalaCheck     % "test"
  }
}
