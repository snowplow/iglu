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

import cats.Show
import cats.data.{EitherNel, EitherT}
import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}

import com.snowplowanalytics.iglu.ctl.commands._
import com.snowplowanalytics.iglu.ctl.Common.Error

object Main extends IOApp {

  // Check -> Folder does not exist. Common. Input folder presence is checked in Main

  // generate
  // Read -> (inaccessible, not a JSON, not a Schema) -- filter out a file entirely, but do not short-circuit the process
  // Read -> (incompatible path) -- short-circuit the process
  // Read -> load everything into memory
  // Lint -> on very permissive set of checks (only errors)
  // Write -> short circuits at any point
  // Write -> takes force into account

  // static push
  // Read -> (inaccessible, not a JSON, not a Schema) -- filter out a file entirely, but do not short-circuit the process
  // Read -> (incompatible path) -- short-circuit the process
  // Read -> load everything into memory
  // Lint -> on very permissive set of checks (only errors)
  // Write -> short circuits at any point
  // Write -> takes force into account

  // static s3cp
  // Read -> (inaccessible) -- short-circuit the process
  // Read -> does not load into memory (it can be big)
  // Read -> does not even parse them



  def run(args: List[String]): IO[ExitCode] = {
    val result: Result = Command.parse(args) match {
      case Right(Command.Lint(input, skipWarnings, skipChecks)) =>
        Lint.process(input, skipChecks, skipWarnings)

      case Right(Command.StaticGenerate(in, out, schema, own, size, jp, raw, split, noheader, f)) =>
        Generate.process(in, out, jp, raw, schema, size, split, noheader, f, own)
      case Right(Command.StaticPush(input, registryRoot, apikey, public)) =>
        Push.process(input, registryRoot, apikey, public)
      case Right(Command.StaticS3Cp(input, bucket, s3Path, accessKey, secretKey, profile, region)) =>
        S3cp.process(input, bucket, s3Path, accessKey, secretKey, profile, region)

      case Right(Command.StaticDeploy(config)) =>
        Deploy.process(config)
      case Left(e) =>
        EitherT.fromEither[IO](Error.Message(e.toString).asLeft[List[String]].toEitherNel)
    }

    result.value.flatMap(processResult[String])
  }

  def processResult[A: Show](either: EitherNel[Error, List[A]]): IO[ExitCode] =
    either.fold(
      errors => errors.traverse_(e => IO(System.err.println(e.show))) *> IO.pure(ExitCode.Error),
      messages => messages.traverse_(e => IO(System.out.println(e.show))) *> IO.pure(ExitCode.Success)
    )
}
