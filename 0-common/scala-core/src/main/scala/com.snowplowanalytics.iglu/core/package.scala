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
package com.snowplowanalytics.iglu

// Scala
import scala.language.implicitConversions

/**
 * Package object containing Ops
 * Importing `com.snowplowanalytics.iglu.core._` assumes these Ops classes
 * will implicitly add defined methods.
 */
package object core {

  /**
   * This implicit class will add extraction methods to all types
   * with defined [[ExtractFrom]] instance
   */
  implicit final class ExtractFromOps[E: ExtractFrom](entity: E) extends ExtractFrom.ExtractFromOps[E](entity)

  /**
   * This implicit class will add attachment methods to all types
   * with defined [[AttachTo]] instance
   */
  implicit final class AttachToOps[E: AttachTo](entity: E) extends AttachTo.AttachToOps[E](entity)

}
