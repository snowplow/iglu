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
import com.github.fge.jackson.jsonpointer.{ JsonPointer => FgeJsonPointer }
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.core.keyword.syntax.checkers.AbstractSyntaxChecker
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration
import com.github.fge.jsonschema.core.report.{ListProcessingReport, ProcessingMessage, ProcessingReport}
import com.github.fge.jsonschema.core.tree.SchemaTree
import com.github.fge.jsonschema.library.{DraftV4Library, Keyword => FgeKeyword}
import com.github.fge.jsonschema.processors.syntax.SyntaxValidator
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.msgsimple.bundle.MessageBundle

import scala.collection.JavaConverters._

import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.validated._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{ fromJsonNode, asJsonNode }
import org.json4s.jackson.compactJson

// Iglu Core
import com.snowplowanalytics.iglu.core.SchemaKey
import com.snowplowanalytics.iglu.core.json4s.Json4sIgluCodecs.SchemaVerSerializer

import Linter.Message

/** Specific to FGE Validator logic, responsible for linting schemas against meta-schema */
object SelfSyntaxChecker extends AbstractSyntaxChecker("self", NodeType.OBJECT) {

  private implicit val schemaFormats: Formats = DefaultFormats + SchemaVerSerializer

  private lazy val instance = SelfSyntaxChecker.getSyntaxValidator

  /** Transform typical `ProcessingMessage` into a message accompanied with `JsonPointer` */
  def extractCheckerMessage(json: JValue): Message =
    json match {
      case JObject(fields) =>
        val jsonObject = fields.toMap
        val pointer = for {
          JObject(schemaFields) <- jsonObject.get("schema")
          JString(pointer) <- schemaFields.toMap.get("pointer")
        } yield Pointer.parseSchemaPointer(pointer).fold(identity, identity)
        val keyword = jsonObject.get("keyword").flatMap {
          case JString(kw) => Some(kw)
          case _ => None
        }
        val message = jsonObject.getOrElse("message", JString("Unknown message from SelfSyntaxChecker")) match {
          case JString(m) => m.capitalize + keyword.map(x => s", in [$x]").getOrElse("")
          case unknown => s"Unrecognized message from SelfSyntaxChecker [${compactJson(unknown)}]"
        }
        val level = extractLevel(json)
        Message(pointer.getOrElse(Pointer.Root), message, level)
      case _ =>
        Message(Pointer.Root, "Unknown message from SelfSyntaxChecker", Linter.Level.Info)
    }

  def extractReport(processingMessage: ProcessingMessage) =
    extractCheckerMessage(fromJsonNode(processingMessage.asJson))

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
  def validateSchema(json: JValue, skipWarnings: Boolean): ValidatedNel[Message, Unit] = {
    val jsonNode = asJsonNode(json)
    val report = instance.validateSchema(jsonNode)
      .asInstanceOf[ListProcessingReport]

      report.iterator.asScala.map(extractReport).filter(filterMessages(skipWarnings)).toList match {
      case Nil => ().valid
      case h :: t => NonEmptyList.of(h, t: _*).invalid
    }
  }

  private def extractLevel(message: JValue): Linter.Level =
    message \ "level" match {
      case JString(l) if l.toLowerCase == "error" => Linter.Level.Error
      case JString(l) if l.toLowerCase == "warning" => Linter.Level.Warning
      case JString(l) if l.toLowerCase == "info" => Linter.Level.Info
      case _ => Linter.Level.Info
    }

  /**
    * Build predicate to filter messages with log level less than WARNING
    *
    * @param skipWarning curried arg to produce predicate
    * @param message validation message produced by FGE validator
    * @return always true if `skipWarnings` is false, otherwise depends on loglevel
    */
  private def filterMessages(skipWarning: Boolean)(message: Message): Boolean =
    if (skipWarning) message.level match {
      case Linter.Level.Warning => false
      case Linter.Level.Info => false
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
  def checkValue(pointers: java.util.Collection[FgeJsonPointer],
                 bundle: MessageBundle,
                 report: ProcessingReport,
                 tree: SchemaTree): Unit = {

    val value = fromJsonNode(getNode(tree))
    value.extractOpt[SchemaKey] match {
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
    val selfKeyword = FgeKeyword.newBuilder("self")
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
