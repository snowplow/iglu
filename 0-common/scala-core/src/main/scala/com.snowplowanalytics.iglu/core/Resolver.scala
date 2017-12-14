/*
 * Copyright (c) 2016-2017 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.core

/**
  * Entity allowing to fetch and validate schemas for entities of `A`
  * Resolvers supposed to be implemented as separate artifacts
  *
  * @tparam F effect, wrapping resolver's work (such as `Either[String, Option[A]]` or `IO[A]`
  * @tparam A AST for data and schema
  */
trait Resolver[F[_], A] {
  /** Lookup for a schema, attached to data */
  def lookup(data: SelfDescribingData[A]): F[SelfDescribingSchema[A]]

  /** Validate self-describing data against some schema */
  def validate(data: SelfDescribingData[A], schema: SelfDescribingSchema[A]): F[Either[String, Unit]]
}
