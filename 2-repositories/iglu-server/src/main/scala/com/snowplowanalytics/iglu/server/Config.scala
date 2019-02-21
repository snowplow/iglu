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
package com.snowplowanalytics.iglu.server

import java.nio.file.Path

import cats.implicits._
import cats.effect.IO

import com.monovore.decline._

import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.module.http4s._

import migrations.MigrateFrom
import generated.BuildInfo.version

case class Config(database: Config.StorageConfig,
                  repoServer: Config.Http,
                  debug: Option[Boolean],
                  patchesAllowed: Option[Boolean],
                  webhooks: Option[Config.Webhooks])

object Config {

  sealed trait StorageConfig
  object StorageConfig {
    case object Dummy extends StorageConfig
    case class Postgres(host: String,
                        port: Int,
                        dbname: String,
                        username: String,
                        password: String,
                        driver: String,
                        connectThreads: Option[Int]) extends StorageConfig
  }

  case class Http(interface: String, baseUrl: String, port: Int)
  case class Webhooks(schemaPublished: Option[List[Webhook.SchemaPublished]])

  implicit def httpConfigHint =
    ProductHint[Http](ConfigFieldMapping(CamelCase, CamelCase))

  sealed trait ServerCommand {
    def config: Path
    def read: IO[Either[String, Config]] =
      IO(pureconfig.loadConfigFromFiles[Config](List(config)).leftMap(_.toList.map(_.description).mkString("\n")))
  }

  object ServerCommand {
    case class Run(config: Path) extends ServerCommand
    case class Setup(config: Path, migrate: Option[MigrateFrom]) extends ServerCommand
  }

  val configOpt = Opts.option[Path]("config", "Path to server configuration HOCON")
  val migrateOpt = Opts
    .option[String]("migrate", "Migrate the DB from a particular version")
    .mapValidated { s => MigrateFrom.parse(s).toValid(s"Cannot perform migration from version $s to $version").toValidatedNel }
    .orNone

  val runCommand: Opts[ServerCommand] =
    Opts.subcommand("run", "Run Iglu Server")(configOpt).map(ServerCommand.Run.apply)
  val setupCommand: Opts[ServerCommand] =
    Opts.subcommand("setup", "Setup Iglu Server")((configOpt, migrateOpt).mapN(ServerCommand.Setup.apply))

  val serverCommand = Command[ServerCommand](generated.BuildInfo.name, generated.BuildInfo.version)(runCommand.orElse(setupCommand))
}
