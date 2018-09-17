/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
import sbt._

object Dependencies {
  val resolutionRepos = Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.typesafeIvyRepo("releases")
  )

  object V {
    // Scala
    val scopt = "3.7.0"
    val schemaDdl = "0.9.0"
    val akka  = "2.4.20"
    val joda = "1.6"
    val jodaTime = "2.3"
    val json4s = "3.2.11"
    val jsonValidator = "2.2.5"
    val postgres = "9.1-901-1.jdbc4"
    val slf4j = "1.7.25"
    val slick = "2.1.0"
    val slickpg = "0.6.0"
    val akkaHttp = "10.0.11"
    val swaggerAkkaHttp = "0.13.0"

    // Scala (test only)
    val specs2    = "4.0.2"
  }

  object Libraries {
    // Scala
    val scopt         = "com.github.scopt"      %% "scopt"                 % V.scopt
    val schemaDdl     = "com.snowplowanalytics" %% "schema-ddl"            % V.schemaDdl
    val akkaActor     = "com.typesafe.akka"     %% "akka-actor"            % V.akka
    val akkaSlf4j     = "com.typesafe.akka"     %% "akka-slf4j"            % V.akka
    val joda          = "org.joda"              %  "joda-convert"          % V.joda
    val jodaTime      = "joda-time"             %  "joda-time"             % V.jodaTime
    val json4s        = "org.json4s"            %% "json4s-jackson"        % V.json4s
    val jsonValidator = "com.github.fge"        %  "json-schema-validator" % V.jsonValidator
    val postgres      = "postgresql"            %  "postgresql"            % V.postgres
    val slick         = "com.typesafe.slick"    %% "slick"                 % V.slick
    val slickpg       = "com.github.tminglei"   %% "slick-pg"              % V.slickpg
    val slickpgJoda   = "com.github.tminglei"   %% "slick-pg_joda-time"    % V.slickpg
    val slf4j         = "org.slf4j"             %  "slf4j-simple"          % V.slf4j
    val akkaHttp      = "com.typesafe.akka"     %% "akka-http"             % V.akkaHttp
    val swaggerAkkaHttp = "com.github.swagger-akka-http" %% "swagger-akka-http" % V.swaggerAkkaHttp

    // Scala (test only)
    val akkaTestKit   = "com.typesafe.akka"  %%  "akka-testkit"  % V.akka   % "test"
    val specs2        = "org.specs2"         %%  "specs2-core"   % V.specs2 % "test"
    val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % V.akkaHttp % Test

  }
}
