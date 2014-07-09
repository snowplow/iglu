/* 
 * Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.repositories.scalaserver

// This project
import core.SchemaActor

// Java
import java.io.File

// Scala
import scala.concurrent.duration._

// Akka and Spray
import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

//Argot
import org.clapper.argot._

//Config
import com.typesafe.config.{ ConfigFactory, Config, ConfigException }

//Logging
import org.slf4j.LoggerFactory

object ScalaRepoServer extends App {
  lazy val log = LoggerFactory.getLogger(getClass())
  import log.{ error, debug, info, trace }

  import ArgotConverters._

  val parser = new ArgotParser(
    programName = generated.Settings.name,
    compactUsage = true,
    preUsage = Some("%s: Version %s. Copyright (c) 2014, %s".format(
      generated.Settings.name,
      generated.Settings.version,
      generated.Settings.organization)
    )
  )

  val config = parser.option[Config](List("config"), "filename",
    "Configuration file. Defaults to \"resources/application.conf\" " +
      "(within .jar) if not set") {
        (c, opt) =>
          val file = new File(c)
          if (file.exists) {
            ConfigFactory.parseFile(file)
          } else {
            parser.usage("Configuration file \"%s\" does not exist".format(c))
            ConfigFactory.empty()
          }
      }
  parser.parse(args)

  val rawConf = config.value.getOrElse(ConfigFactory.load("application"))
  val repoConfig = new RepoConfig(rawConf)

  implicit val system = ActorSystem("scala-repo-server")

  implicit val timeout = Timeout(5.seconds)
  val service = system.actorOf(Props[RepoServiceActor], "repo-service")
  //tmp
  val schemaActor = system.actorOf(Props[SchemaActor], "schema-actor")

  IO(Http) ? Http.Bind(service, interface = repoConfig.interface,
                       port = repoConfig.port)
}

class RepoConfig(config: Config) {
  private val repoServer = config.getConfig("repo-server")
  val interface = repoServer.getString("interface")
  val port = repoServer.getInt("port")
}
