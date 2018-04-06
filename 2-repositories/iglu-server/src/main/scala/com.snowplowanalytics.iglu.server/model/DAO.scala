/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
import org.json4s.JValue
import org.json4s.jackson.Serialization.writePretty

/**
 * Trait to be mixed in with every DAO.
 */
trait DAO {

  implicit val formats = DefaultFormats

  /**
   * Case class defined for json formatting. Handles usual responses.
   */
  case class Result(status: Int, message: String)

  /**
   * Case class defined for json formatting. Handles json responses when a
   * schemas is created.
   */
  case class ResultAdded(status: Int, message: String, location: String)

  /**
   * Case class defined for json formatting. Handles json responses when a
   * schema failed validation.
   */
  case class ResultReport(status: Int, message: String, report: JValue)

  /**
   * Formats a (status and message) pair to proper json.
   * @param status the response's status
   * @param message the response's message
   * @return a well-formatted json
   */
  def result(status: Int, message: String): String =
    writePretty(Result(status, message))

  /**
   * Formats a (status, message, location) tuple to proper json.
   * @param status the reponse's status
   * @param message the response's message
   * @param location the newly added schema's location
   * @return a well-formatted json
   */
  def result(status: Int, message: String, location: String): String=
    writePretty(ResultAdded(status, message, location))

  /**
   * Formats a (status, message, report) tuple to proper json.
   * @param status the response's status
   * @param message the response's message
   * @param report the reponse's validation failure report
   * @return a well-formatted json
   */
  def result(status: Int, message: String, report: JValue): String =
    writePretty(ResultReport(status, message, report))
}
