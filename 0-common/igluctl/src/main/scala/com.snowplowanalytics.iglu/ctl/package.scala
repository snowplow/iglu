/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu

import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO

import fs2.Stream
import org.json4s.JValue

import com.snowplowanalytics.iglu.core.SelfDescribingSchema

package object ctl {
  /** Anything that can bear error message */
  type Failing[A] = EitherT[IO, Common.Error, A]

  /**
    * Result of command execution,
    * bearing either non-empty list of lines to output to stderr and exit with 1,
    * or possibly empty list (silent) of messages to output to stdout and exit with 0
    */
  type Result = EitherT[IO, NonEmptyList[Common.Error], List[String]]

  /** Stream that does not fail on invalid input (to process remaining successes) */
  type ReadStream[A] = Stream[IO, Either[Common.Error, A]]

  /** File path and content ready to be written to file system */
  type TextFile = File[String]

  /** JSON content and reference on filesystem */
  type JsonFile = File[JValue]

  type SchemaFile = File[SelfDescribingSchema[JValue]]
}
