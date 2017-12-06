/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.iglu.ctl

// Java
import java.io.File

// JSON Schema Validator
import com.github.fge.jackson.NodeType
import com.github.fge.jackson.jsonpointer.JsonPointer
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.core.keyword.syntax.checkers.AbstractSyntaxChecker
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.core.tree.SchemaTree
import com.github.fge.jsonschema.core.util.URIUtils
import com.github.fge.jsonschema.library.{DraftV4Library, Keyword}
import com.github.fge.jsonschema.processors.syntax.SyntaxValidator
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.fge.msgsimple.bundle.MessageBundle

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.fromJsonNode

// Iglu Core
import com.snowplowanalytics.iglu.core.SchemaKey
import com.snowplowanalytics.iglu.core.json4s.Json4sIgluCodecs.SchemaVerSerializer

/**
 * Syntax Checker used to validate `self` key in Self-describing JSON Schema
 * Must be used with `com.github.fge.jsonschema` `Library` to declare that
 * `self` is valid and expected object for all Schemas with
 * `http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#`
 * URI in `\$schema` keyword
 */
object SelfSyntaxChecker extends AbstractSyntaxChecker("self", NodeType.OBJECT) {

  private implicit val schemaFormats: Formats = DefaultFormats + SchemaVerSerializer

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
  def checkValue(
      pointers: java.util.Collection[JsonPointer],
      bundle: MessageBundle,
      report: ProcessingReport,
      tree: SchemaTree): Unit = {

    val value = fromJsonNode(getNode(tree))
    value.extractOpt[SchemaKey] match {
      case Some(_) => ()
      case None => report.error(newMsg(tree, bundle, "iglu.invalidSchemaKey").putArgument("value", value))
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
    val cwdFile = new File(System.getProperty("user.dir", ".")).getCanonicalFile
    val cwd = URIUtils.normalizeURI(cwdFile.toURI).toString

    val builder = URITranslatorConfiguration.newBuilder().setNamespace(cwd)

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
