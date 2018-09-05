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
package com.snowplowanalytics.iglu.schemaddl.jsonschema

import com.github.fge.jackson.NodeType
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.core.keyword.syntax.checkers.AbstractSyntaxChecker
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration
import com.github.fge.jsonschema.core.report.{ListProcessingReport, ProcessingMessage, ProcessingReport}
import com.github.fge.jsonschema.core.tree.SchemaTree
import com.github.fge.jsonschema.library.{DraftV4Library, Keyword}
import com.github.fge.jsonschema.processors.syntax.SyntaxValidator
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.msgsimple.bundle.MessageBundle

import scala.collection.JavaConverters._

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.validated._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{ fromJsonNode, asJsonNode }

// Iglu Core
import com.snowplowanalytics.iglu.core.SchemaMap
import com.snowplowanalytics.iglu.core.json4s.Json4sIgluCodecs.SchemaVerSerializer

/** Specific to FGE Validator logic, responsible for linting schemas against meta-schema */
object SelfSyntaxChecker extends AbstractSyntaxChecker("self", NodeType.OBJECT) {

  private implicit val schemaFormats: Formats = DefaultFormats + SchemaVerSerializer

  private lazy val instance = SelfSyntaxChecker.getSyntaxValidator

  /**
    * Validate syntax of JSON Schema using FGE JSON Schema Validator
    * Entry-point method
    *
    * @param json JSON supposed to be JSON Schema
    * @param skipWarnings whether to count warning (such as unknown keywords) as
    *                    errors or silently ignore them
    * @return non-empty list of error messages in case of invalid Schema
    *         or unit if case of successful
    */
  def validateSchema(json: JValue, skipWarnings: Boolean): ValidatedNel[String, Unit] = {
    val jsonNode = asJsonNode(json)
    val report = instance.validateSchema(jsonNode)
      .asInstanceOf[ListProcessingReport]

    report.iterator.asScala.filter(filterMessages(skipWarnings)).map(_.toString).toList match {
      case Nil => ().valid
      case h :: t => NonEmptyList.of(h, t: _*).invalid
    }
  }

  /**
    * Build predicate to filter messages with log level less than WARNING
    *
    * @param skipWarning curried arg to produce predicate
    * @param message validation message produced by FGE validator
    * @return always true if `skipWarnings` is false, otherwise depends on loglevel
    */
  private def filterMessages(skipWarning: Boolean)(message: ProcessingMessage): Boolean =
    if (skipWarning) fromJsonNode(message.asJson()) \ "level" match {
      case JString("warning") => false
      case JString("info") => false
      case JString("debug") => false
      case _ => true
    }
    else true


  /**
    * Default `SyntaxChecker` method to process key
    * The only required method for `SyntaxChecker`, others are factory methods
    *
    * @param pointers list of JSON Pointers to fill
    * @param bundle message bundle to use
    * @param report processing report to use
    * @param tree currently processing part of Schema
    */
  @throws[ProcessingException]("If key is invalid")
  def checkValue(pointers: java.util.Collection[JsonPointer],
                 bundle: MessageBundle,
                 report: ProcessingReport,
                 tree: SchemaTree): Unit = {

    val value = fromJsonNode(getNode(tree))
    value.extractOpt[SchemaMap] match {
      case Some(_) => ()
      case None => report.error(newMsg(tree, bundle, "iglu.invalidSchemaMap").putArgument("value", value))
    }
  }

  // Factory methods

  /**
    * Get `ValidationConfiguration` object with validator defined for `self`
    * keyword, ready to be used for `Validator` construction
    */
  def getValidationConfiguration: ValidationConfiguration = {
    val selfKeyword = Keyword.newBuilder("self")
      .withSimpleDigester(NodeType.OBJECT)
      .withSyntaxChecker(SelfSyntaxChecker)
      .freeze()

    val library = DraftV4Library.get()
      .thaw()
      .addKeyword(selfKeyword)
      .freeze()

    val validationConfiguration = ValidationConfiguration
      .newBuilder()
      .addLibrary("http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0", library)
      .freeze()

    validationConfiguration
  }

  /**
    * Build `SyntaxValidator` object with `SelfSyntaxChecker`.
    * This is a main object used to validate Schemas
    */
  def getSyntaxValidator: SyntaxValidator = {
    val builder = URITranslatorConfiguration.newBuilder()

    val cfg = LoadingConfiguration.newBuilder()
      .setURITranslatorConfiguration(builder.freeze())
      .freeze()

    JsonSchemaFactory.newBuilder()
      .setLoadingConfiguration(cfg)
      .setValidationConfiguration(getValidationConfiguration)
      .freeze()
      .getSyntaxValidator
  }
}
