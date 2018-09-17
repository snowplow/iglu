/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
import util.ServerConfig

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
   * @param permission API key's permission
   * @param isPublic whether or not the schema is publicly available
   */
  case class AddSchema(vendor: String, name: String, format: String,
    version: String, draftNumber: String, schema: String, owner: String, permission: String,
    isPublic: Boolean = false, isDraft: Boolean)

  /**
   * Message to send in order to update or create a schema based on its
   * (vendor, name, format, version) tuple.
   * @param vendor schema's vendor
   * @param name schema's name
   * @param format schema's format
   * @param version schema's version
   * @param schema schema to be updated
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @param isPublic wheter or not the schema is publicly available
   */
  case class UpdateSchema(vendor: String, name: String, format: String,
    version: String, draftNumber: String, schema: String, owner: String, permission: String,
    isPublic: Boolean = false, isDraft: Boolean)

  /**
   * Message to send in order to delete a schema based on its
   * (vendor, name, format, version) tuple.
   * @param vendor schema's vendor
   * @param name schema's name
   * @param format schema's format
   * @param version schema's version
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   * @param isPublic whether or not the schema is publicly available
   */
  case class DeleteSchema(vendor: String, name: String, format: String, version: String, draftNumber: String,
    owner: String, permission: String, isPublic: Boolean = false, isDraft: Boolean)

  /**
   * Message to send in order to retrieve every public schema.
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetPublicSchemas(owner: String, permission: String, includeMetadata: Boolean, isDraft: Boolean)

  /**
   * Message to send in order to retrieve every public schema's metadata.
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetPublicMetadata(owner: String, permission: String, isDraft: Boolean)

  /**
   * Message to send in order to retrieve a schema based on its
   * (vendor, name, format, version) tuple.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param versions list of schemas' versions
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetSchema(vendors: String, names: String,
    formats: String, versions: String, draftNumbers: String, owner: String,
    permission: String, includeMetadata: Boolean, isDraft: Boolean)

  /**
   * Message to send in order to retrieve metadata about a schema based on its
   * (vendor, name, format, version) tuple.
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetMetadata(vendors: String, names: String,
    formats: String, versions: String, draftNumbers: String, owner: String,
    permission: String, isDraft: Boolean)

  /**
   * Message to send in order to get every version of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetSchemasFromFormat(vendors: String, names: String,
    formats: String, owner: String, permission: String, includeMetadata: Boolean, isDraft: Boolean)

  /**
   * Message to send in order to get metadata about every version of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param formats list of schemas' formats
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetMetadataFromFormat(vendors: String, names: String,
    formats: String, owner: String, permission: String, isDraft: Boolean)

  /**
   * Message to send in order to retrieve every format, version combination of
   * a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetSchemasFromName(vendors: String, names: String,
    owner: String, permission: String, includeMetadata: Boolean, isDraft: Boolean)

  /**
   * Message to send in order to retrieve metadata about every format, version
   * combination of a schema.
   * @param vendors list of schemas' vendors
   * @param names list of schemas' names
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetMetadataFromName(vendors: String, names: String,
    owner: String, permission: String, isDraft: Boolean)

  /**
   * Message to send in order to retrieve every schema belonging to a vendor.
   * @param vendors list of schemas' vendors
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetSchemasFromVendor(vendors: String, owner: String,
    permission: String, includeMetadata: Boolean, isDraft: Boolean)

  /**
   * Message to send in order to retrieve metadata about every schema belonging
   * to a vendor.
   * @param vendors list of schemas' vendors
   * @param owner the owner of the API key the request was made with
   * @param permission API key's permission
   */
  case class GetMetadataFromVendor(vendors: String, owner: String, permission: String, isDraft: Boolean)

  /**
   * Message to send in order to validate that a schema is self-describing.
   * @param schema schema to be validated
   * @param format schema's format
   * otherwise
   */
  case class ValidateSchema(schema: String, format: String)

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
class SchemaActor(serverConfig: ServerConfig) extends Actor {

  import SchemaActor._

  // Schema model
  private val schema = new SchemaDAO(serverConfig.db)

  /**
   * Method specifying how the actor should handle the incoming messages.
   */
  def receive = {

    case AddSchema(v, n, f, vs, dn, s, o, p, i, d) =>
      sender ! schema.add(v, n, f, vs, dn, s, o, p, i, d)

    case UpdateSchema(v, n, f, vs, dn, s, o, p, i, d) =>
      sender ! schema.update(v, n, f, vs, dn, s, o, p, i, d)

    case DeleteSchema(v, n, f, vs, dn, o, p, i, d) =>
      sender ! schema.delete(v, n, f, vs, dn, o, p, i, d)

    case GetPublicSchemas(o, p, i, d) => sender ! schema.getPublicSchemas(o, p, i, d)

    case GetPublicMetadata(o, p, i) => sender ! schema.getPublicMetadata(o, p, i)

    case GetSchema(v, n, f, vs, dn, o, p, i, d) => sender ! schema.get(v, n, f, vs, dn, o, p, i, d)

    case GetMetadata(v, n, f, vs, dn, o, p, i) =>
      sender ! schema.getMetadata(v, n, f, vs, dn, o, p, i)

    case GetSchemasFromFormat(v, n, f, o, p, i, d) =>
      sender ! schema.getFromFormat(v, n, f, o, p, i, d)

    case GetMetadataFromFormat(v, n, f, o, p, i) =>
      sender ! schema.getMetadataFromFormat(v, n, f, o, p, i)

    case GetSchemasFromName(v, n, o, p, i, d) =>
      sender ! schema.getFromName(v, n, o, p, i, d)

    case GetMetadataFromName(v, n, o, p, i) =>
      sender ! schema.getMetadataFromName(v, n, o, p, i)

    case GetSchemasFromVendor(v, o, p, i, d) => sender ! schema.getFromVendor(v, o, p, i, d)

    case GetMetadataFromVendor(v, o, p, i) =>
      sender ! schema.getMetadataFromVendor(v, o, p, i)

    case ValidateSchema(s, f) => sender ! schema.lintSchema(s, f)

    case Validate(v, n, f, vs, i) => sender ! schema.validate(v, n, f, vs, i)
  }
}
