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

import cats.effect.{IOApp, Resource, SyncIO}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext

trait SafeIOApp extends IOApp.WithContext {

  implicit val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  private val log: Logger = LoggerFactory.getLogger(Main.getClass)

  final val exitingEC: ExecutionContext = new ExecutionContext {
    def execute(r: Runnable): Unit =
      ec.execute { () =>
        try r.run()
        catch {
          case t: Throwable =>
            log.error("Unhandled error occurred... Shutting down", t)
            System.exit(-1)
        }
      }

    def reportFailure(cause: Throwable): Unit =
      ec.reportFailure(cause)
  }

  val executionContextResource: Resource[SyncIO, ExecutionContext] =
    Resource.liftF(SyncIO(exitingEC))
}
