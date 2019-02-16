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
import java.io.File

import sbt._
import Keys._

import com.typesafe.sbt.packager.Keys.{daemonUser, maintainer}
import com.typesafe.sbt.packager.docker._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

object BuildSettings {

  lazy val dockerPgInstallCmds = Seq(
    ExecCmd("RUN", "cp", "/opt/docker/docker-entrypoint.sh", "/usr/local/bin/"),
    Cmd("RUN", "apt update"),
    Cmd("RUN", "mkdir -p /usr/share/man/man7"),
    Cmd("RUN", "apt install -y postgresql-client-9.6")
  )

  lazy val dockerSettings = Seq(
    // Use single entrypoint script for all apps
    sourceDirectory in Universal := new File(baseDirectory.value, "scripts"),
    dockerRepository := Some("snowplow-docker-registry.bintray.io"),
    dockerUsername := Some("snowplow"),
    dockerBaseImage := "snowplow-docker-registry.bintray.io/snowplow/base-debian:0.1.0",
    maintainer in Docker := "Snowplow Analytics Ltd. <support@snowplowanalytics.com>",
    daemonUser in Docker := "root",  // Will be gosu'ed by docker-entrypoint.sh
    dockerEntrypoint := Seq("docker-entrypoint.sh"),
    dockerCommands ++= BuildSettings.dockerPgInstallCmds,
    dockerCmd := Seq("--help")
  )
}
