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

object Dependencies {

  object V {
    // Java
    val jsonValidator    = "2.2.6"
    val codemodel        = "2.6"
    // Scala
    val igluCoreJson4s   = "0.1.0"
    val schemaddl        = "0.5.0"
    val scopt            = "3.5.0"
    val scalaz7          = "7.0.8"
    val json4s           = "3.2.11"
    val scalajHttp       = "2.3.0"
    val awscala          = "0.5.7"
    // Scala (test only)
    val specs2           = "2.3.13"
    val scalazSpecs2     = "0.2"
    val scalaCheck       = "1.12.2"
  }


  object Libraries {
    // Java
    val jsonValidator    = "com.github.fge"             %  "json-schema-validator"     % V.jsonValidator
    val codemodel        = "com.sun.codemodel"          %  "codemodel"                 % V.codemodel
    // Scala
    val igluCoreJson4s   = "com.snowplowanalytics"      %% "iglu-core-json4s"          % V.igluCoreJson4s
    val schemaddl        = "com.snowplowanalytics"      %% "schema-ddl"                % V.schemaddl
    val scopt            = "com.github.scopt"           %% "scopt"                     % V.scopt
    val scalaz7          = "org.scalaz"                 %% "scalaz-core"               % V.scalaz7
    val json4sJackson    = "org.json4s"                 %% "json4s-jackson"            % V.json4s
    val scalajHttp       = "org.scalaj"                 %% "scalaj-http"               % V.scalajHttp
    val awscala          = "com.github.seratch"         %% "awscala"                   % V.awscala

    // Scala (test only)
    val specs2           = "org.specs2"                 %% "specs2"                    % V.specs2         % "test"
    val scalazSpecs2     = "org.typelevel"              %% "scalaz-specs2"             % V.scalazSpecs2   % "test"
    val scalaCheck       = "org.scalacheck"             %% "scalacheck"                % V.scalaCheck     % "test"
  }
}
