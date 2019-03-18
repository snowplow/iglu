/*
 * Copyright (c) 2019 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
import sbt._

object Dependencies {

  object V {
    val IgluCore   = "0.4.0"
    val SchemaDdl  = "0.9.0"
    val IgluClient = "0.6.0-M2"

    val Fs2        = "1.0.3"
    val Http4s     = "0.20.0-M6"
    val Rho        = "0.19.0-M6"
    val Doobie     = "0.6.0"
    val Decline    = "0.6.0"
    val Cats       = "1.6.0"
    val CatsEffect = "1.2.0"
    val Log4Cats   = "0.3.0"
    val Circe      = "0.11.1"
    val CirceFs2   = "0.11.0"
    val Refined    = "0.9.3"
    val PureConfig = "0.10.2"
    val SwaggerUi  = "3.20.5"

    val Specs2     = "4.3.6"
    val Logback    = "1.2.3"
  }

  val all = Seq(
    "com.snowplowanalytics" %% "iglu-core-circe"       % V.IgluCore,
    "com.snowplowanalytics" %% "schema-ddl"            % V.SchemaDdl,
    "com.snowplowanalytics" %% "iglu-scala-client"     % V.IgluClient,

    "co.fs2"                %% "fs2-core"              % V.Fs2,
    "com.monovore"          %% "decline"               % V.Decline,
    "org.typelevel"         %% "cats-core"             % V.Cats,
    "io.chrisdavenport"     %% "log4cats-slf4j"        % V.Log4Cats,
    "org.http4s"            %% "http4s-blaze-server"   % V.Http4s,
    "org.http4s"            %% "http4s-blaze-client"   % V.Http4s,
    "org.http4s"            %% "http4s-circe"          % V.Http4s,
    "org.http4s"            %% "http4s-dsl"            % V.Http4s,
    "org.http4s"            %% "rho-swagger"           % V.Rho,
    "io.circe"              %% "circe-generic"         % V.Circe,
    "io.circe"              %% "circe-java8"           % V.Circe,
    "io.circe"              %% "circe-jawn"            % V.Circe,
    "io.circe"              %% "circe-literal"         % V.Circe,
    "io.circe"              %% "circe-refined"         % V.Circe,
    "io.circe"              %% "circe-fs2"             % V.CirceFs2,
    "eu.timepit"            %% "refined"               % V.Refined,
    "com.github.pureconfig" %% "pureconfig"            % V.PureConfig,
    "com.github.pureconfig" %% "pureconfig-http4s"     % V.PureConfig,
    "org.tpolecat"          %% "doobie-core"           % V.Doobie,
    "org.tpolecat"          %% "doobie-postgres"       % V.Doobie,
    "org.tpolecat"          %% "doobie-postgres-circe" % V.Doobie,
    "org.tpolecat"          %% "doobie-hikari"         % V.Doobie,

    "org.webjars"           %  "swagger-ui"            % V.SwaggerUi,
    "ch.qos.logback"        %  "logback-classic"       % V.Logback,
    "org.tpolecat"          %% "doobie-specs2"         % V.Doobie     % "test",
    "org.specs2"            %% "specs2-core"           % V.Specs2     % "test"
  )
}
