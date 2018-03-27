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
package com.snowplowanalytics.iglu.server

// Java
import java.io.File

// config
import com.typesafe.config.ConfigFactory

// scopt
import scopt.OptionParser

// This project
import util.ServerConfig

/**
  * CLI container
  *
  * @param config Input config file
  */
case class IgluServerCLI(config: File = new File("."))

/**
  * Starting point of Iglu Server
  */
object Boot {
  def main(args: Array[String]): Unit = {

    // scopt definitions
    val parser = new OptionParser[IgluServerCLI](generated.Settings.name) {
      head(generated.Settings.name, generated.Settings.version)
      help("help") text "Print this help message"
      version("version") text "Print version info\n"

      opt[File]("config").required().valueName("<filename>")
        .action((f: File, c: IgluServerCLI) => c.copy(f))
        .text("Path to custom config file. It is required and can not be empty.")
        .validate( f =>
          if (f.exists)
            if (f.isFile)
              if (f.canRead)
                success
              else
                failure(s"Configuration $f is not readable.")
            else
              failure(s"Configuration $f is not a file.")
          else
            failure(s"Configuration $f does not exist")
        )
    }

    // parse CLI args
    parser.parse(args, IgluServerCLI()) match {
      case Some(c) =>
        // parse configuration file
        val resolvedConfig = ConfigFactory.parseFile(c.config).resolve()
        if (resolvedConfig.isEmpty) {
          Console.err.println(s"Error: Configuration file ${c.config.getPath} is empty\n" +
                              "Try --help for more information.")
          System.exit(1)
        }
        val config = ServerConfig(resolvedConfig)
        val server = new IgluServer(config)
        server.start()

      case None    =>
        // scopt will show error messages
        System.exit(1)
    }

  }
}
