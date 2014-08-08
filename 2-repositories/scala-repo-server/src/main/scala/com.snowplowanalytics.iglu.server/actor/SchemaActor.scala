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
import model.SchemaDAO
import util.Config

// Akka
import akka.actor.Actor

/**
 * Object regrouping every message the ``SchemaActor`` can receive.
 */
object SchemaActor {

  /**
   * Message to send in order to add a schema based on its
   * (vendor, name, format, version) tuple, validates that the schemas
   * does not already exist.
   */
  case class AddSchema(vendor: String, name: String, format: String,
    version: String, schema: String)

  /**
   * Message to send in order to retrieve a schema based on its
   * (vendor, name, format, version) tuple.
   */
  case class GetSchema(vendor: String, name: String, format: String,
    version: String)

  /**
   * Message to send in order to retrieve metadata about a schema based on its
   * (vendor, name, format, version) tuple.
   */
  case class GetMetadata(vendor: String, name: String, format: String,
    version: String)

  /**
   * Message to send in order to get every version of a schema.
   */
  case class GetSchemasFromFormat(vendor: String, name: String, format: String)

  /**
   * Message to send in order to get metadata about every version of a schema.
   */
  case class GetMetadataFromFormat(vendor: String, name: String, format: String)

  /**
   * Message to send in order to retrieve every format, version combination of
   * a schema.
   */
  case class GetSchemasFromName(vendor: String, name: String)

  /**
   * Message to send in order to retrieve metadata about every format, version
   * combination of a schema.
   */
  case class GetMetadataFromName(vendor: String, name: String)

  /**
   * Message to send in order to retrieve every schema belonging to a vendor.
   */
  case class GetSchemasFromVendor(vendor: String)

  /**
   * Message to send in order to retrieve metadata about every schema belonging
   * to a vendor.
   */
  case class GetMetadataFromVendor(vendor: String)

  /**
   * Message to send in order to validate that a schema is self-describing.
   */
  case class Validate(json: String)
}

/**
 * Schema actor interfacing the services and the schema model.
 */
class SchemaActor extends Actor {

  import SchemaActor._

  // Schema model
  private val schema = new SchemaDAO(Config.db)

  /**
   * Method specifying how the actor should handle the incoming messages.
   */
  def receive = {

    // Send the result of the DAO's add method back to message's sender
    case AddSchema(v, n, f, vs, s) => sender ! schema.add(v, n, f, vs, s)

    // Send the result of the DAO's get method back to the message's sender
    case GetSchema(v, n, f, vs) => sender ! schema.get(v, n, f, vs)

    // Send the result of the DAO's getMetadata method back to the message's
    // sender
    case GetMetadata(v, n, f, vs) => sender ! schema.getMetadata(v, n, f, vs)

    // Send the result of the DAO's getFromFormat method back to the
    // message's sender
    case GetSchemasFromFormat(v, n, f) =>
      sender ! schema.getFromFormat(v, n, f)

    // Send the result of the DAO's getMetadataFromFormat back to the
    // message's sender
    case GetMetadataFromFormat(v, n, f) =>
      sender ! schema.getMetadataFromFormat(v, n, f)

    // Send the result of the DAO's getFromName method back to the message's
    // sender
    case GetSchemasFromName(v, n) => sender ! schema.getFromName(v, n)

    // Send the result of the DAO's getMetadataFromName method back to the
    // message's sender
    case GetMetadataFromName(v, n) => sender ! schema.getMetadataFromName(v, n)

    // Send the result of the DAO's getFromVendor method back to the message's
    // sender
    case GetSchemasFromVendor(v) => sender ! schema.getFromVendor(v)

    // Send the result of the DAO's getMetadataFromVendr method back to the
    // message's sender
    case GetMetadataFromVendor(v) => sender ! schema.getMetadataFromVendor(v)

    //Send the result of the DAO's validate method back to the message's sender
    case Validate(j) => sender ! schema.validate(j)
  }
}
