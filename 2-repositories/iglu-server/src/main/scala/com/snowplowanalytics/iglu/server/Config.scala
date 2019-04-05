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

import io.circe.{Encoder, Json, JsonObject}
import io.circe.generic.semiauto._

import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto._
import pureconfig.module.http4s._
import migrations.MigrateFrom

import generated.BuildInfo.version

/**
  * Case class containing the Iglu Server's configuration options,
  * derived from a Typesafe Config configuration file.
  *
  * @param database       Database used by the server, either dummy (in-memory storage) or postgres instance
  * @param repoServer     Configuration options for the Iglu server - interface, port and deployment address
  * @param debug          If true, enables an additional endpoint (/api/debug) that outputs all internal state
  * @param patchesAllowed If true, schemas sent to the /api/schemas endpoint will overwrite existing ones rather than
  *                       be skipped if a schema with the same key already exists
  * @param webhooks       List of webhooks triggered by specific actions or endpoints
  */
case class Config(database: Config.StorageConfig,
                  repoServer: Config.Http,
                  debug: Option[Boolean],
                  patchesAllowed: Option[Boolean],
                  webhooks: Option[List[Webhook]])

object Config {

  sealed trait StorageConfig
  object StorageConfig {

    /**
      * Dummy in-memory configuration.
      */
    case object Dummy extends StorageConfig

    /**
      * Configuration for PostgreSQL state storage.
      */
    case class Postgres(host: String,
                        port: Int,
                        dbname: String,
                        username: String,
                        password: String,
                        driver: String,
                        connectThreads: Option[Int],
                        maxPoolSize: Option[Int]) extends StorageConfig

    val postgresReader = ConfigReader.forProduct8("host", "port","dbname", "username",
      "password", "driver", "connectThreads", "maxPoolSize")(StorageConfig.Postgres.apply)

    implicit val storageConfigCirceEncoder: Encoder[StorageConfig] =
      deriveEncoder[StorageConfig].mapJson { json =>
        json.hcursor
          .downField("Postgres")
          .focus
          .getOrElse(Json.Null)
          .mapObject { o => JsonObject.fromMap(o.toMap.map {
            case ("password", _) => ("password", Json.fromString("******"))
            case (k, v) => (k, v)
          })}
      }
  }

  /**
    * Configuration options for the Iglu server.
    *
    * @param interface The server's host.
    * @param port The server's port.
    */
  case class Http(interface: String, port: Int)

  implicit def httpConfigHint =
    ProductHint[Http](ConfigFieldMapping(CamelCase, CamelCase))

  implicit val httpConfigCirceEncoder: Encoder[Http] =
    deriveEncoder[Http]

  implicit val pureWebhookReader: ConfigReader[Webhook] = ConfigReader.fromCursor { cur =>
    for {
      objCur <- cur.asObjectCursor
      uriCursor <- objCur.atKey("uri")
      uri <- ConfigReader[org.http4s.Uri].from(uriCursor)

      prefixes <- objCur.atKeyOrUndefined("vendor-prefixes") match {
        case keyCur if keyCur.isUndefined => List.empty.asRight
        case keyCur => keyCur.asList.flatMap(_.traverse(cur => cur.asString))
      }
    } yield Webhook.SchemaPublished(uri, Some(prefixes))
  }

  implicit val pureStorageReader: ConfigReader[StorageConfig] = ConfigReader.fromCursor { cur =>
    for {
      objCur  <- cur.asObjectCursor
      typeCur <- objCur.atKey("type")
      typeStr <- typeCur.asString
      result  <- typeStr match {
        case "postgres" => StorageConfig.postgresReader.from(cur)
        case "dummy" => StorageConfig.Dummy.asRight
        case _ =>
          val message = s"type has value $typeStr instead of class1 or class2"
          objCur.failed[StorageConfig](error.CannotConvert(objCur.value.toString, "StorageConfig", message))
      }
    } yield result
  }

  implicit val pureHttpReader: ConfigReader[Http] = deriveReader[Http]

  implicit val pureWebhooksReader: ConfigReader[List[Webhook]] = ConfigReader.fromCursor { cur =>
    for {
      objCur  <- cur.asObjectCursor
      schemaPublishedCursors <- objCur.atKeyOrUndefined("schema-published").asList
      webhooks <- schemaPublishedCursors.traverse(cur => pureWebhookReader.from(cur))
    } yield webhooks
  }

  implicit val pureConfigReader: ConfigReader[Config] = deriveReader[Config]

  implicit val mainConfigCirceEncoder: Encoder[Config] =
    deriveEncoder[Config]

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

  val runCommand: Opts[ServerCommand] = configOpt.map(ServerCommand.Run.apply)
  val setupCommand: Opts[ServerCommand] =
    Opts.subcommand("setup", "Setup Iglu Server")((configOpt, migrateOpt).mapN(ServerCommand.Setup.apply))

  val serverCommand = Command[ServerCommand](generated.BuildInfo.name, generated.BuildInfo.version)(runCommand.orElse(setupCommand))
}
