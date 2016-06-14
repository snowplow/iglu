/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.schemaddl
package utils

// Scala
import scala.collection.immutable.ListMap

/**
 * Utilities for manipulating Maps
 */
object MapUtils {

  /**
   * Organising all of the key -> value pairs in the Map by alphabetical order
   *
   * @param paths The Map that needs to be ordered.
   * @return an ordered ListMap of paths that are now in
   *         alphabetical order.
   */
  def getOrderedMap(paths: Map[String, Map[String, String]]): ListMap[String, Map[String, String]] =
    ListMap(paths.toSeq.sortBy(_._1):_*)
}
