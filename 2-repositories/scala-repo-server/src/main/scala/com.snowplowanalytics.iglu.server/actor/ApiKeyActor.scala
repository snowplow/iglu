/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0,
* and you may not use this file except in compliance with the
* Apache License Version 2.0.
* You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the Apache License Version 2.0 is distributed on
* an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.  See the Apache License Version 2.0 for the specific
* language governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server
package actor

// This project
import model.ApiKeyDAO
import util.Config

// Akka
import akka.actor.Actor

// Java
import java.util.UUID

/**
 * Object regrouping every message the ``ApiKeyActor`` can receive.
 */
object ApiKeyActor {

  /**
   * Message to send in order to retrieve a (owner, permission) pair if the
   * key exists.
   */
  case class GetKey(uid: String)

  /**
   * Message to send in order to add a (write, read) pair of keys for the
   * specified owner if it is not conflicting with an existing one.
   */
  case class AddBothKey(owner: String)

  /**
   * Message to send in order to delete a key specifying its uuid.
   */
  case class DeleteKey(uid: String)

  /**
   * Message to send in order to delete every keys belonging to the specified
   * owner.
   */
  case class DeleteKeys(owner: String)
}

/**
 * ApiKey actor interfacing between the services and the api key model.
 */
class ApiKeyActor extends Actor {

  import ApiKeyActor._

  // ApiKey model
  val apiKey = new ApiKeyDAO(Config.db)

  /**
   * Method specifying how the actor should handle the incoming messages.
   */
  def receive = {

    // Send the result of the DAO's get method back to the message's sender
    case GetKey(uid) => sender ! apiKey.get(uid)

    // Send the result of the DAO's addreadwrite method back to the message's
    // sender
    case AddBothKey(owner) => sender ! apiKey.addReadWrite(owner)

    // Send the result of the DAO's delete method back to the message's sender
    case DeleteKey(uid) => sender ! apiKey.delete(uid)

    // Send the result of the DAO's deleteFromowner method back to the message's
    // sender
    case DeleteKeys(owner) => sender ! apiKey.deleteFromOwner(owner)
  }
}
