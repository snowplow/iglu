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

import java.nio.file.Paths

import org.http4s.Uri

import io.circe.syntax._
import io.circe.literal._

import cats.syntax.either._

class ConfigSpec extends org.specs2.Specification { def is = s2"""
  parse run command without file $e1
  parse run config from file $e2
  parse run config with dummy DB from file $e3
  asJson hides postgres password $e4
  """

  def e1 = {
    val input = "--config foo.hocon"
    val expected = Config.ServerCommand.Run(Paths.get("foo.hocon"))
    val result = Config.serverCommand.parse(input.split(" ").toList)
    result must beRight(expected)
  }

  def e2 = {
    val config = getClass.getResource("/valid-pg-config.conf").toURI
    val configPath = Paths.get(config)
    val input = s"--config ${configPath}"
    val expected = Config(
      Config.StorageConfig.Postgres("postgres", 5432, "igludb", "sp_user", "sp_password", "org.postgresql.Driver", None, Some(5)),
      Config.Http("0.0.0.0", 8080),
      Some(true),
      Some(true),
      Some(List(
        Webhook.SchemaPublished(Uri.uri("https://example.com/endpoint"), Some(List.empty)),
        Webhook.SchemaPublished(Uri.uri("https://example2.com/endpoint"), Some(List("com", "org.acme", "org.snowplow")))
      ))
    )
    val result = Config
      .serverCommand.parse(input.split(" ").toList)
      .leftMap(_.toString)
      .flatMap(_.read.unsafeRunSync())
    result must beRight(expected)
  }

  def e3 = {
    val config = getClass.getResource("/valid-dummy-config.conf").toURI
    val configPath = Paths.get(config)
    val input = s"--config ${configPath}"
    val expected = Config(Config.StorageConfig.Dummy, Config.Http("0.0.0.0", 8080), Some(true), None, None)
    val result = Config
      .serverCommand.parse(input.split(" ").toList)
      .leftMap(_.toString)
      .flatMap(_.read.unsafeRunSync())
    result must beRight(expected)
  }

  def e4 = {
    val input = Config(
      Config.StorageConfig.Postgres("postgres", 5432, "igludb", "sp_user", "sp_password", "org.postgresql.Driver", None, Some(5)),
      Config.Http("0.0.0.0", 8080),
      Some(true),
      Some(true),
      Some(List(
        Webhook.SchemaPublished(Uri.uri("https://example.com/endpoint"), Some(List.empty)),
        Webhook.SchemaPublished(Uri.uri("https://example2.com/endpoint"), Some(List("com", "org.acme", "org.snowplow")))
      ))
    )

    val expected = json"""{
      "database" : {
        "connectThreads" : null,
        "username" : "sp_user",
        "host" : "postgres",
        "dbname" : "igludb",
        "port" : 5432,
        "driver" : "org.postgresql.Driver",
        "maxPoolSize" : 5,
        "password" : "******"
      },
      "repoServer" : {
        "interface" : "0.0.0.0",
        "port" : 8080
      },
      "debug" : true,
      "patchesAllowed" : true,
      "webhooks" : [
        {
          "SchemaPublished" : {
            "uri" : "https://example.com/endpoint",
            "vendorPrefixes" : [
            ]
          }
        },
        {
          "SchemaPublished" : {
            "uri" : "https://example2.com/endpoint",
            "vendorPrefixes" : [
              "com",
              "org.acme",
              "org.snowplow"
            ]
          }
        }
      ]
    }"""

    input.asJson must beEqualTo(expected)
  }
}
