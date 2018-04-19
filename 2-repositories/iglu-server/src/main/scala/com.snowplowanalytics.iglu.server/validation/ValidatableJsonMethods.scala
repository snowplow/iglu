/*
 * Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.server
package validation

// Jackson
import com.github.fge.jackson.JsonLoader
import com.fasterxml.jackson.databind.JsonNode

// JSON Schema
import com.github.fge.jsonschema.SchemaVersion
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.main.{
  JsonSchemaFactory,
  JsonValidator
}
import com.github.fge.jsonschema.core.report.{
  ListReportProvider,
  ProcessingMessage,
  LogLevel
}

// Scala
import scala.collection.JavaConversions._

// Scalaz
import scalaz._
import Scalaz._

object ValidatableJsonMethods {

  private[validation] lazy val JsonSchemaValidator =
    getJsonSchemaValidator(SchemaVersion.DRAFTV4)

  /**
   * Implicit to pimp a JsonNode to our
   * Scalaz Validation-friendly version.
   *
   * @param instance A JsonNode
   * @return the pimped ValidatableJsonNode
   */
  //implicit def pimpJsonNode(instance: JsonNode) =
  //  new ValidatableJsonNode(instance)

  /**
   * Validates a JSON against a given
   * JSON Schema. On Success, simply
   * passes through the original JSON.
   * On Failure, return a NonEmptyList
   * of failure messages.
   *
   * @param instance The JSON to validate
   * @param schema The JSON Schema to
   *        validate the JSON against
   * @return either Success boxing the
   *         JsonNode, or a Failure boxing
   *         a NonEmptyList of
   *         ProcessingMessages
   */
  def validateAgainstSchema(instance: JsonNode, schema: JsonNode):
  ValidatedNel[JsonNode] = {

    val report = JsonSchemaValidator.validateUnchecked(schema, instance)
    val msgs = report.iterator.toList
    msgs match {
      case x :: xs if !report.isSuccess =>
        NonEmptyList[ProcessingMessage](x, xs: _*).failure
      case Nil if report.isSuccess => instance.success
      case _ => throw new RuntimeException(s"""validation report success
        ${report.isSuccess} conflicts with message count ${msgs.length}""")
    }
  }

  /**
   * Validates a self-describing JSON against
   * its specified JSON Schema.
   *
   * IMPORTANT: currently the exact version of
   * the JSON Schema (i.e. MODEL-REVISION-ADDITION)
   * must be resolvable thru Iglu.
   *
   * @param instance The self-describing JSON
   *         to validate
   * @param dataOnly Whether the returned JsonNode
   *        should be the data only, or the whole
   *        JSON (schema + data)
   * @return either Success boxing the JsonNode
   *         or a Failure boxing a NonEmptyList
   *         of ProcessingMessages
   */
  //def validate(instance: JsonNode, dataOnly: Boolean = false):
  //ValidatedNel[JsonNode] =
  //  for {
  //    j  <- validateAsSelfDescribing(instance)
  //    s  =  j.get("schema").asText
  //    d  =  j.get("data")
  //    js <- resolver.lookupSchema(s)
  //    v  <- validateAgainstSchema(d, js)
  //  } yield if (dataOnly) d else instance

  /**
   * The same as validate(), but on Success returns
   * a tuple containing the SchemaKey as well as
   * the JsonNode.
   *
   * IMPORTANT: currently the exact version of
   * the JSON Schema (i.e. MODEL-REVISION-ADDITION)
   * must be resolvable thru Iglu.
   *
   * @param instance The self-describing JSON
   *         to validate
   * @param dataOnly Whether the returned JsonNode
   *        should be the data only, or the whole
   *        JSON (schema + data)
   * @return either Success boxing a Tuple2 of the
   *         JSON's SchemaKey plus its JsonNode,
   *         or a Failure boxing a NonEmptyList
   *         of ProcessingMessages
   */
  //def validateAndIdentifySchema(instance: JsonNode, dataOnly: Boolean = false):
  //ValidatedNel[JsonSchemaPair] =
  //  for {
  //    j  <- validateAsSelfDescribing(instance)
  //    s  =  j.get("schema").asText
  //    d  =  j.get("data")
  //    sk <- SchemaKey.parseNel(s)
  //    js <- resolver.lookupSchema(sk)
  //    v  <- validateAgainstSchema(d, js)
  //  } yield if (dataOnly) (sk, d) else (sk, instance)

  /**
   * Verify that this JSON is of the expected schema,
   * then validate it against the schema.
   *
   * IMPORTANT: currently the exact version of
   * the JSON Schema (i.e. MODEL-REVISION-ADDITION)
   * must be resolvable thru Iglu.
   *
   * @param instance The self-describing JSON to
   *        verify and validate
   * @param schemaKey Identifying the schema we
   *        believe this JSON is described by
   * @param dataOnly Whether the returned JsonNode
   *        should be the data only, or the whole
   *        JSON (schema + data)
   * @return either Success boxing the JsonNode
   *         or a Failure boxing a NonEmptyList
   *         of ProcessingMessages
   */
  //def verifySchemaAndValidate(instance: JsonNode, schemaKey: SchemaKey,
  //dataOnly: Boolean = false): ValidatedNel[JsonNode] =
  //  for {
  //    j  <- validateAsSelfDescribing(instance)
  //    s  =  j.get("schema").asText
  //    d  =  j.get("data")
  //    sk <- SchemaKey.parseNel(s)
  //    m  <- if (sk == schemaKey)
  //            sk.success
  //          else
  //            s"Verifying schema as ${schemaKey} failed: found ${sk}".
  //              toProcessingMessageNel.fail
  //    js <- resolver.lookupSchema(m)
  //    v  <- validateAgainstSchema(d, js)
  //  } yield if (dataOnly) d else instance

  /**
   * Get our schema for self-describing Iglu instances.
   *
   * Unsafe lookup is fine here because we know this
   * schema exists in our resources folder
   */
  //private[validation] def getSelfDescribingSchema: JsonNode =
  //  resolver.unsafeLookupSchema(
  //    SchemaKey("com.snowplowanalytics.self-desc", "instance-iglu-only", "jsonschema", "1-0-0")
  //  )

  /**
   * Validates that this JSON is a self-
   * describing JSON.
   *
   * @param instance The JSON to check
   * @return either Success boxing the
   *         JsonNode, or a Failure boxing
   *         a NonEmptyList of
   *         ProcessingMessages
   */
  //private[validation] def validateAsSelfDescribing(instance: JsonNode):
  //ValidatedNel[JsonNode] = {
  //  validateAgainstSchema(instance, getSelfDescribingSchema)
  //}

  /**
   * Factory for retrieving a JSON Schema
   * validator with the specific version.
   *
   * @param version The version of the JSON
   *        Schema spec to validate against
   * @return a JsonValidator
   */
  private[validation] def getJsonSchemaValidator(version: SchemaVersion):
  JsonValidator = {
    
    // Override the ReportProvider so we never throw Exceptions and only collect ERRORS+
    val rep = new ListReportProvider(LogLevel.ERROR, LogLevel.NONE)
    val cfg = ValidationConfiguration
                .newBuilder
                .setDefaultVersion(version)
                .freeze
    val fac = JsonSchemaFactory
                .newBuilder
                .setReportProvider(rep)
                .setValidationConfiguration(cfg)
                .freeze
    
    fac.getValidator
  }
}

/**
 * A pimped JsonNode which supports validation
 * using JSON Schema.
 */
//class ValidatableJsonNode(instance: JsonNode) {
//
//  import validation.{ValidatableJsonMethods => VJM}
//
//  def validateAgainstSchema(schema: JsonNode): ValidatedNel[JsonNode] = 
//    VJM.validateAgainstSchema(instance, schema)
//
//  def validate(dataOnly: Boolean): ValidatedNel[JsonNode] =
//    VJM.validate(instance, dataOnly)
//
//  def validateAndIdentifySchema(dataOnly: Boolean):
//  ValidatedNel[JsonSchemaPair] =
//    VJM.validateAndIdentifySchema(instance, dataOnly)
//
//  def verifySchemaAndValidate(schemaKey: SchemaKey, dataOnly: Boolean):
//  ValidatedNel[JsonNode] =
//    VJM.verifySchemaAndValidate(instance, schemaKey, dataOnly)
//}
