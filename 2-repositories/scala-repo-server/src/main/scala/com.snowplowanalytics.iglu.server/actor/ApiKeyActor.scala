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

object ApiKeyActor {
  case class GetKey(uid: String)
  case class AddBothKey(owner: String)
  case class DeleteKey(uid: String)
  case class DeleteKeys(owner: String)
}

class ApiKeyActor extends Actor {
  import ApiKeyActor._

  val apiKey = new ApiKeyDAO(Config.db)

  def receive = {
    case GetKey(uid) => sender ! apiKey.get(uid)
    case AddBothKey(owner) => sender ! apiKey.addReadWrite(owner)
    case DeleteKey(uid) => sender ! apiKey.delete(uid)
    case DeleteKeys(owner) => sender ! apiKey.deleteFromOwner(owner)
  }
}
