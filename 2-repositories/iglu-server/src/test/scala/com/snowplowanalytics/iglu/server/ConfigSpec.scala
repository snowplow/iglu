package com.snowplowanalytics.iglu.server

import java.nio.file.Paths

import cats.syntax.either._

class ConfigSpec extends org.specs2.Specification { def is = s2"""
  parse run command without file $e1
  parse run config from file $e2
  parse run config with dummy DB from file $e3
  """

  def e1 = {
    val input = "run --config foo.hocon"
    val expected = Config.ServerCommand.Run(Paths.get("foo.hocon"))
    val result = Config.serverCommand.parse(input.split(" ").toList)
    result must beRight(expected)
  }

  def e2 = {
    val config = getClass.getResource("/valid-pg-config.conf").toURI
    val configPath = Paths.get(config)
    val input = s"run --config ${configPath}"
    val expected = Config(
      Config.StorageConfig.Postgres("postgres", 5432, "igludb", "sp_user", "sp_password", "org.postgresql.Driver", None),
      Config.Http("0.0.0.0", "localhost:8080", 8080),
      Some(true),
      None,
      List(
        Config.SchemaPublishedWebhook("https://example.com/endpoint",List()),
        Config.SchemaPublishedWebhook("https://example2.com/endpoint",List("com", "org.acme", "org.snowplow"))
      )
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
    val input = s"run --config ${configPath}"
    val expected = Config(Config.StorageConfig.Dummy, Config.Http("0.0.0.0", "localhost:8080", 8080), Some(true), None, List())
    val result = Config
      .serverCommand.parse(input.split(" ").toList)
      .leftMap(_.toString)
      .flatMap(_.read.unsafeRunSync())
    result must beRight(expected)
  }
}
