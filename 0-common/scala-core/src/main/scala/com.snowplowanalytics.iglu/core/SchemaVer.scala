/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
 * Class holding semantic version for Schema
 *
 * @param model Schema MODEL, representing independent Schema
 * @param revision Schema REVISION, representing backward-incompatible changes
 * @param addition Schema ADDITION, representing backward-compatible changes
 */
case class SchemaVer(model: Int, revision: Int, addition: Int) {
  def asString = s"$model-$revision-$addition"
}

object SchemaVer {
  /**
   * Regular expression to validate SchemaVer
   */
  val schemaVerRegex = "\\d+-\\d+-\\d+".r

  /**
   * Regular expression to extract MODEL, REVISION, ADDITION
   */
  val modelRevisionAdditionRegex = "([0-9]+)-([0-9]+)-([0-9]+)".r

  /**
   * Extract the model, revision, and addition of the SchemaVer
   *
   * @return some SchemaVer or None
   */
  def parse(version: String): Option[SchemaVer] = version match {
    case modelRevisionAdditionRegex(m, r, a) => Some(SchemaVer(m.toInt, r.toInt, a.toInt))
    case _ => None
  }
}
