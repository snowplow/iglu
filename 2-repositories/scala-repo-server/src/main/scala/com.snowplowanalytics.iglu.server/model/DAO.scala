/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
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
package model

// Json4s
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.writePretty

/**
 * Trait to be mixed in with every DAO.
 */
trait DAO {

  implicit val formats = DefaultFormats

  /**
   * Case class defined for json formatting.
   */
  case class Result(status: Int, message: String)

  /**
   * Formats a (status and message to proper json.
   * @param status the response's status
   * @param message the response's message
   * @return a well-formatted json
   */
  def result(status: Int, message: String): String =
    writePretty(Result(status, message))
}
