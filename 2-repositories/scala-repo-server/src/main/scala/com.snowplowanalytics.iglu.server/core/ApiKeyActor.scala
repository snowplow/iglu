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
package core

// This project
import model.ApiKeyDAO

// Akka
import akka.actor.Actor

// Java
import java.util.UUID

object ApiKeyActor {
  case class GetKey(uid: UUID)
  case class AddBothKey(owner: String)
  case class DeleteKey(uid: UUID)
}

class ApiKeyActor extends Actor {
  import ApiKeyActor._

  def receive = {
    case GetKey(uid) => sender ! ApiKeyDAO.get(uid)
    case AddBothKey(owner) => sender ! ApiKeyDAO.addReadWrite(owner)
    case DeleteKey(uid) => sender ! ApiKeyDAO.delete(uid)
  }
}
