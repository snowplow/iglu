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

import cats.data.EitherT
import cats.effect._
import cats.syntax.functor._
import cats.syntax.either._

object Main extends SafeIOApp {

  def run(args: List[String]) = {
    val cli = for {
      command <- EitherT.fromEither[IO](Config.serverCommand.parse(args).leftMap(_.toString))
      config  <- EitherT[IO, String, Config](command.read)
      result  <- command match {
        case _: Config.ServerCommand.Run =>
          EitherT.liftF[IO, String, ExitCode](Server.run(config).compile.lastOrError )
        case Config.ServerCommand.Setup(_, migration) =>
          EitherT.liftF[IO, String, ExitCode](Server.setup(config, migration))
      }
    } yield result

    cli.value.flatMap {
      case Right(code) => IO.pure(code)
      case Left(cliError) => IO(System.err.println(cliError)).as(ExitCode.Error)
    }
  }
}
