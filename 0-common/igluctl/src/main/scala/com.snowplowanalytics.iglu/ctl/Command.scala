/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.ctl

// scalaz
import scalaz._

// Java
import java.io.File
import java.util.UUID

// scopt
import scopt.OptionParser

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SanityLinter.{ SeverityLevel, FirstLevel, SecondLevel }

// This library
import PushCommand._

/**
 * Common command container
 */
case class Command(
  // common
  command:         Option[String]  = None,
  input:           Option[File]    = None,

  // ddl
  output:          Option[File]    = None,
  db:              String          = "redshift",
  withJsonPaths:   Boolean         = false,
  rawMode:         Boolean         = false,
  schema:          Option[String]  = None,
  varcharSize:     Int             = 4096,
  splitProduct:    Boolean         = false,
  noHeader:        Boolean         = false,
  force:           Boolean         = false,

  // sync
  registryRoot:    Option[HttpUrl] = None,
  apiKey:          Option[UUID]    = None,
  isPublic:        Boolean         = false,

  // lint
  skipWarnings:    Boolean         = false,
  severityLevel:   SeverityLevel   = FirstLevel,

  // s3
  bucket:          Option[String]  = None,
  s3path:          Option[String]  = None,
  accessKeyId:     Option[String]  = None,
  secretAccessKey: Option[String]  = None,
  profile:         Option[String]  = None,
  region:          Option[String]  = None,

  // java
  packageSuffix:   String          = ""

) {
  def toCommand: Option[Command.CtlCommand] = command match {
    case Some("static generate") => Some(
      GenerateCommand(input.get, output.getOrElse(new File(".")), db,withJsonPaths, rawMode, schema, varcharSize, splitProduct, noHeader, force))
    case Some("static push") =>
      Some(PushCommand(registryRoot.get, apiKey.get, input.get, isPublic))
    case Some("static s3cp") =>
      Some(S3cpCommand(input.get, bucket.get, s3path, accessKeyId, secretAccessKey, profile, region))
    case Some("static java") =>
      Some(GenerateJavaCommand(input.get, output.getOrElse(new File(".")), packageSuffix))
    case Some("lint") =>
      Some(LintCommand(input.get, skipWarnings, severityLevel))
    case _ =>
      None
  }
}

object Command {

  // Type class instance to parse UUID
  implicit val uuidRead = scopt.Read.reads(UUID.fromString)
  
  implicit val httpUrlRead: scopt.Read[HttpUrl] = scopt.Read.reads { s =>
    PushCommand.parseRegistryRoot(s) match {
      case \/-(httpUrl) => httpUrl
      case -\/(e) => throw e
    }
  }

  implicit val severityLevelRead: scopt.Read[SeverityLevel] = scopt.Read.reads {
    case "1" => FirstLevel
    case "2" => SecondLevel
    case l => throw new IllegalArgumentException(s"Error: $l is invalid severity level")
  }

  private def subcommand(sub: String)(unit: Unit, root: Command): Command =
    root.copy(command = root.command.map(_ + " " + sub))

  /**
   * Trait common for all commands
   */
  private[ctl] trait CtlCommand

  def inputReadable(c: Command): Either[String, Unit] =
    c.input match {
      case Some(input) if input.exists() && input.canRead => Right(())
      case Some(input) => Left(s"Input [${input.getAbsolutePath}] isn't available for read")
      case _ => Right(())
    }

  val cliParser = new OptionParser[Command]("igluctl") {

    head(generated.ProjectSettings.name, generated.ProjectSettings.version)
    help("help") text "Print this help message"
    version("version") text "Print version info\n"

    checkConfig(inputReadable)

    cmd("static")
      .action { (_, c) => c.copy(command = Some("static")) }
      .text("Static Iglu generator\n")
      .children(

        cmd("generate")
          .action { (_, c) => c.copy(command = c.command.map(_ + " generate")) }
          .text("Generate DDL out of JSON Schema\n")
          .children(

            arg[File]("input")
              action { (x, c) => c.copy(input = Some(x)) } required()
              text "Path to single JSON schema or directory with JSON Schemas",

            opt[File]("output")
              action { (x, c) => c.copy(output = Some(x)) }
              valueName "<path>"
              text "Directory to put generated data\t\tDefault: current dir",

            opt[String]("dbschema")
              action { (x, c) => c.copy(schema = Some(x)) }
              valueName "<name>"
              text "Redshift schema name\t\t\t\tDefault: atomic",

            opt[String]("db")
              action { (x, c) => c.copy(db = x) }
              valueName "<name>"
              text "DB to which we need to generate DDL\t\tDefault: redshift",

            opt[Int]("varchar-size")
              action { (x, c) => c.copy(varcharSize = x) }
              valueName "<n>"
              text "Default size for varchar data type\t\tDefault: 4096",

            opt[Unit]("with-json-paths")
              action { (_, c) => c.copy(withJsonPaths = true) }
              text "Produce JSON Paths files with DDL",

            opt[Unit]("raw-mode")
              action { (_, c) => c.copy(rawMode = true) }
              text "Produce raw DDL without Snowplow-specific data",

            opt[Unit]("split-product")
              action { (_, c) => c.copy(splitProduct = true) }
              text "Split product types (like [string,integer]) into separate columns",

            opt[Unit]("no-header")
              action { (_, c) => c.copy(noHeader = true) }
              text "Do not place header comments into output DDL",

            opt[Unit]("force")
              action { (_, c) => c.copy(force = true) }
              text "Force override existing manually-edited files\n",

            checkConfig {
              case command: Command if command.withJsonPaths && command.splitProduct =>
                failure("Options --with-json-paths and --split-product cannot be used together")
              case _ => success
            }
          ),

        cmd("push")
          .action(subcommand("push"))
          .text("Upload Schemas from folder into the Iglu registry\n")
          .children(

            arg[File]("input") required()
              action { (x, c) => c.copy(input = Some(x))}
              text "Path to directory with JSON Schemas",

            arg[HttpUrl]("registryRoot")
              action { (x, c) => c.copy(registryRoot = Some(x))}
              text "Iglu Registry registry root to upload Schemas",

            arg[UUID]("apikey")
              action { (x, c) => c.copy(apiKey = Some(x))}
              text "Master API Key",

            opt[Unit]("public")
              action { (_, c) => c.copy(isPublic = true)}
              text "Upload all schemas as public\n"

          ),

        cmd("s3cp")
          .action(subcommand("s3cp"))
          .text("Upload Schema Registry onto Amazon S3\n")
          .children(

            arg[File]("input") required()
              action { (x, c) => c.copy(input = Some(x))}
              text "Path to directory with JSON Schemas",

            arg[String]("bucket") required()
              action { (x, c) => c.copy(bucket = Some(x))}
              text "Bucket name to upload Schemas",

            opt[String]("s3path")
              action { (x, c) => c.copy(s3path = Some(x))}
              text "Path in the bucket to upload Schemas\t\tDefault: bucket root",

            opt[String]("accessKeyId") optional()
              action { (x, c) => c.copy(accessKeyId = Some(x))}
              valueName "<key>"
              text "AWS Access Key Id",

            opt[String]("secretAccessKey")
              action { (x, c) => c.copy(secretAccessKey = Some(x))}
              valueName "<key>"
              text "AWS Secret Access Key",

            opt[String]("profile")
              action { (x, c) => c.copy(profile = Some(x))}
              valueName "<name>"
              text "AWS Profile",

            opt[String]("region")
              action { (x, c) => c.copy(region = Some(x))}
              valueName "<name>"
              text "AWS S3 region\t\t\t\tDefault: us-west-2\n",

            checkConfig { (c: Command) =>
              (c.secretAccessKey, c.accessKeyId, c.profile) match {
                case (Some(_), Some(_), None) => success
                case (None, None, Some(_)) => success
                case (None, None, None) => success
                case _ => failure("You need provide either both accessKeyId and secretAccessKey OR just profile OR have credentials in other lookup places")
              }
            }
          ),

        cmd("java")
          .action(subcommand("java"))
          .text("Generate Java POJO builders")
          .children(
            arg[File]("input") required()
              action { (x, c) => c.copy(input = Some(x))}
              text "Path to directory with JSON Schemas",

            opt[String]("package-suffix")
              action { (x, c) => c.copy(packageSuffix = x)}
              valueName "<string>"
              text "Suffix to apply to vendor name to construct package name of generated class for a schema",

            opt[File]("output")
              action { (x, c) => c.copy(output = Some(x)) }
              valueName "<path>"
              text "Directory to put generated data\t\tDefault: current dir"
          )
    )

    cmd("lint")
      .action { (_, c) => c.copy(command = Some("lint"))}
      .text("Lint Schemas\n")
      .children(

        arg[File]("input") required() action { (x, c) => c.copy(input = Some(x)) }
          text "Path to directory with JSON Schemas",

        opt[Unit]("skip-warnings")
          action { (_, c) => c.copy(skipWarnings = true) }
          text "Don't output messages with log level less than ERROR",

        opt[SeverityLevel]("severityLevel")
          action { (x, c) => c.copy(severityLevel = x) }
          text "Severity level\t\t\t\tDefault: 1"

      )
  }
}

