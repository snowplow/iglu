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

// cats
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.validated._

// Json4s
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods.asJsonNode

object ValidatableJsonMethods {

  private[validation] lazy val JsonSchemaValidator =
    getJsonSchemaValidator(SchemaVersion.DRAFTV4)

  /**
    * Validates a JSON against a given
    * JSON Schema. On Success, simply
    * passes through the original JSON.
    * On Failure, return a NonEmptyList
    * of failure messages.
    *
    * @param instance The JSON to validate
    * @param schema   The JSON Schema to
    *                 validate the JSON against
    * @return either Success boxing the
    *         JsonNode, or a Failure boxing
    *         a NonEmptyList of
    *         ProcessingMessages
    */
  def validateAgainstSchema(instance: JValue, schemaJson: JValue): ValidatedNel[ProcessingMessage, JsonNode] = {
    val data = asJsonNode(instance)
    val schema = asJsonNode(schemaJson)
    val report = JsonSchemaValidator.validateUnchecked(schema, data)
    val msgs = report.iterator.toList
    msgs match {
      case x :: xs if !report.isSuccess =>
        NonEmptyList.of(x, xs: _*).invalid
      case Nil if report.isSuccess => data.valid
      case _ => throw new RuntimeException(
        s"""validation report success ${report.isSuccess} conflicts with message count ${msgs.length}""")
    }
  }

  /**
    * Factory for retrieving a JSON Schema
    * validator with the specific version.
    *
    * @param version The version of the JSON
    *                Schema spec to validate against
    * @return a JsonValidator
    */
  private[validation] def getJsonSchemaValidator(version: SchemaVersion): JsonValidator = {

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

