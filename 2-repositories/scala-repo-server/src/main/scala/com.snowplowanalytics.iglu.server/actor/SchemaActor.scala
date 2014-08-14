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
   * @param vendor schema's vendor
   * @param name schema's name
   * @param format schema's format
   * @param version schema's version
   * @param schema schema to be added
   * @param owner the owner of the API key the request was made with
   * @param permission API key' permission
   * @param isPublic whether or not the schema is publicly available
   */
  case class AddSchema(vendor: String, name: String, format: String,
    version: String, schema: String, owner: String, permission: String,
    isPublic: Boolean = false)

  /**
   * Message to send in order to retrieve a schema based on its
   * (vendor, name, format, version) tuple.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param versions list of schemas' versions
   * @param owner the owner of the API key the request was made with
   */
  case class GetSchema(vendors: List[String], names: List[String],
    formats: List[String], versions: List[String], owner: String)

  /**
   * Message to send in order to retrieve metadata about a schema based on its
   * (vendor, name, format, version) tuple.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param versions list of schemas' versions
   * @param owner the owner of the API key the request was made with
   */
  case class GetMetadata(vendors: List[String], names: List[String],
    formats: List[String], versions: List[String], owner: String)

  /**
   * Message to send in order to get every version of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param owner the owner of the API key the request was made with
   */
  case class GetSchemasFromFormat(vendors: List[String], names: List[String],
    formats: List[String], owner: String)

  /**
   * Message to send in order to get metadata about every version of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param owner the owner of the API key the request was made with
   */
  case class GetMetadataFromFormat(vendors: List[String], names: List[String],
    formats: List[String], owner: String)

  /**
   * Message to send in order to retrieve every format, version combination of
   * a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param owner the owner of the API key the request was made with
   */
  case class GetSchemasFromName(vendors: List[String], names: List[String],
    owner: String)

  /**
   * Message to send in order to retrieve metadata about every format, version
   * combination of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param owner the owner of the API key the request was made with
   */
  case class GetMetadataFromName(vendors: List[String], names: List[String],
    owner: String)

  /**
   * Message to send in order to retrieve every schema belonging to a vendor.
   * @param vendors list of schemas' vendors
   * @param owner the owner of the API key the request was made with
   */
  case class GetSchemasFromVendor(vendors: List[String], owner: String)

  /**
   * Message to send in order to retrieve metadata about every schema belonging
   * to a vendor.
   * @param vendors list of schemas' vendors
   * @param owner the owner of the API key the request was made with
   */
  case class GetMetadataFromVendor(vendors: List[String], owner: String)

  /**
   * Message to send in order to validate that a schema is self-describing.
   * @param schema schema to be validated
   * @param format schema's format
   * @param provideSchema returns the schema if true or a validation message
   * otherwise
   */
  case class ValidateSchema(schema: String, format: String,
    provideSchema: Boolean = true)

  /**
   * Message to send in order to validate an instance against a schema.
   * @param vendor schema's vendor
   * @param name schema's name
   * @param format schema's format
   * @param version schema's version
   * @param instance instance to be validated
   */
  case class Validate(vendor: String, name: String, format: String,
    version: String, instance: String)
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

    // Sends the result of the DAO's add method back to message's sender
    case AddSchema(v, n, f, vs, s, o, p, i) =>
      sender ! schema.add(v, n, f, vs, s, o, p, i)

    // Sends the result of the DAO's get method back to the message's sender
    case GetSchema(v, n, f, vs, o) => sender ! schema.get(v, n, f, vs, o)

    // Sends the result of the DAO's getMetadata method back to the message's
    // sender
    case GetMetadata(v, n, f, vs, o) =>
      sender ! schema.getMetadata(v, n, f, vs, o)

    // Sends the result of the DAO's getFromFormat method back to the
    // message's sender
    case GetSchemasFromFormat(v, n, f, o) =>
      sender ! schema.getFromFormat(v, n, f, o)

    // Sends the result of the DAO's getMetadataFromFormat back to the
    // message's sender
    case GetMetadataFromFormat(v, n, f, o) =>
      sender ! schema.getMetadataFromFormat(v, n, f, o)

    // Sends the result of the DAO's getFromName method back to the message's
    // sender
    case GetSchemasFromName(v, n, o) => sender ! schema.getFromName(v, n, o)

    // Sends the result of the DAO's getMetadataFromName method back to the
    // message's sender
    case GetMetadataFromName(v, n, o) =>
      sender ! schema.getMetadataFromName(v, n, o)

    // Sends the result of the DAO's getFromVendor method back to the message's
    // sender
    case GetSchemasFromVendor(v, o) => sender ! schema.getFromVendor(v, o)

    // Sends the result of the DAO's getMetadataFromVendr method back to the
    // message's sender
    case GetMetadataFromVendor(v, o) =>
      sender ! schema.getMetadataFromVendor(v, o)

    //Sends the result of the DAO's validateSchema method back to the message's
    //sender
    case ValidateSchema(s, f, p) => sender ! schema.validateSchema(s, f, p)

    //Sends the result of the DAO's validate method back to the message's sender
    case Validate(v, n, f, vs, i) => sender ! schema.validate(v, n, f, vs, i)
  }
}
