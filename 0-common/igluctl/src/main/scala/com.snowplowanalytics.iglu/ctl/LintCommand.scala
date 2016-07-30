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

// Scala
import scala.collection.JavaConversions.asScalaIterator

// scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{ asJsonNode, fromJsonNode }

// Java
import java.io.File

// JSON Schema Validator
import com.github.fge.jsonschema.core.report.{ ListProcessingReport, ProcessingMessage }

// Iglu core
import com.snowplowanalytics.iglu.core.SchemaKey

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.IgluSchema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Schema, SanityLinter }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.Json4sToSchema._


// This library
import FileUtils.{ getJsonFilesStream, JsonFile }

case class LintCommand(inputDir: File, skipWarnings: Boolean) extends Command.CtlCommand {
  import LintCommand._

  /**
   * Primary method running command logic
   */
  def process(): Unit = {
    val jsons = getJsonFilesStream(inputDir, Some(filterJsonSchemas))
    val reports = jsons.map { file => 
      val report = file.map(check)
      flattenReport(report) 
    }
    reports.foldLeft(Total(0, 0))((acc, cur) => acc.add(cur)).exit()
  }

  /**
   * Perform syntax check and sanity lint
   *
   * @param jsonFile valid JSON file, supposed to contain JSON Schema
   * @return [[Report]] ADT, indicating successful lint or containing errors
   */
  def check(jsonFile: JsonFile): Report = {
    val pathCheck = extractSchema(jsonFile).map(_ => ()).validation.toValidationNel
    val syntaxCheck = validateSchema(jsonFile.content, skipWarnings)
    val lintCheck = Schema.parse(jsonFile.content).map(SanityLinter.lint)

    val fullValidation = syntaxCheck |+| pathCheck |+| lintCheck.getOrElse("Doesn't contain JSON Schema".failureNel)

    fullValidation match {
      case scalaz.Success(_) =>
        Success(jsonFile.getKnownPath)
      case scalaz.Failure(list) =>
        SchemaFailure(jsonFile.getKnownPath, list.toList)
    }
  }
}

object LintCommand {

  /**
   * OS-specific separator
   */
  private val separator = System.getProperty("file.separator", "/")

  /**
   * FGE validator for Self-describing schemas
   */
  lazy val validator = SelfSyntaxChecker.getSyntaxValidator

  /**
   * End-of-the-world class, containing info about success/failure of execution
   *
   * @param successes number of successfully validated schemas
   * @param failures number of schemas with errors
   */
  case class Total(successes: Int, failures: Int) {
    /**
     * Exit from app with error status if invalid schemas were found
     */
    def exit(): Unit = {
      println(s"TOTAL: $successes Schemas were successfully validated")
      println(s"TOTAL: $failures errors were encountered")

      if (failures > 0) sys.exit(1)
      else sys.exit(0)
    }

    /**
     * Append and print report for another schema
     *
     * @param report schema processing result
     * @return modified total object
     */
    def add(report: Report): Total = report match {
      case s: Success =>
        println(s"SUCCESS: ${s.asString}")
        copy(successes = successes + 1)
      case f: Failure =>
        println(s"FAILURE: ${f.asString}")
        copy(failures = failures + 1)
      case f: SchemaFailure =>
        println(s"FAILURE: ${f.asString}")
        copy(failures = failures + 1)
    }
  }

  /**
   * Output ADT, representing success or containing errors
   */
  sealed trait Report { def asString: String }

  /**
   * Happens if file with *iglu-compatible path* contains invalid JSON, has
   * invalid access rights or in case of internal app failure
   */
  case class Failure(message: String) extends Report {
    def asString = message
  }

  /**
   * Some syntax error (such as unknown JSON `type`, empty `required`)
   * uncovered by FGE Json Schema Validator.
   * Unknown properties are not errors
   */
  case class SchemaFailure(filePath: String, errors: List[String]) extends Report {
    def asString = s"Schema [$filePath] contains following errors: \n${errors.mkString("\n")}"
  }

  case class Success(filePath: String) extends Report {
    def asString = s"Schema [$filePath] is successfully validated"
  }

  /**
   * Transform failing [[Report]] to plain [[Report]] by transforming
   * left into [[Failure]] (IO/parsing/runtime error)
   *
   * @param result disjunction of string with report
   * @return plain report
   */
  def flattenReport(result: Validation[String, Report]): Report =
    result match {
      case scalaz.Success(status) => status
      case scalaz.Failure(failure) => Failure(failure)
    }

  /**
   * Validate syntax of JSON Schema using FGE JSON Schema Validator
   *
   * @param json JSON supposed to be JSON Schema
   * @param skipWarnings whether to count warning (such as unknown keywords) as
   *                    errors or silently ignore them
   * @return non-empty list of error messages in case of invalid Schema
   *         or unit if case of successful
   */
  def validateSchema(json: JValue, skipWarnings: Boolean): ValidationNel[String, Unit] = {
    val jsonNode = asJsonNode(json)
    val report = validator.validateSchema(jsonNode)
                          .asInstanceOf[ListProcessingReport]

    report.iterator.toList.filter(filterMessages(skipWarnings)).map(_.toString) match {
      case Nil => ().success
      case h :: t => NonEmptyList(h, t: _*).failure
    }
  }

  /**
   * Build predicate to filter messages with log level less than WARNING
   *
   * @param skipWarning curried arg to produce predicate
   * @param message validation message produced by FGE validator
   * @return always true if `skipWarnings` is false, otherwise depends on loglevel
   */
  def filterMessages(skipWarning: Boolean)(message: ProcessingMessage): Boolean =
    if (skipWarning) fromJsonNode(message.asJson()) \ "level" match {
      case JString("warning") => false
      case JString("info") => false
      case JString("debug") => false
      case _ => true
    }
    else true

  /**
   * Predicate used to filter only files which Iglu path contains `jsonschema`
   * as format
   *
   * @param file any real file
   * @return true if third entity of Iglu path is `jsonschema`
   */
  private def filterJsonSchemas(file: File): Boolean =
    file.getAbsolutePath.split(separator).takeRight(4) match {
      case Array(_, _, format, _) => format == "jsonschema"
      case _ => false
    }

  /**
   * Get Iglu-compatible path (com.acme/event/jsonschema/1-0-2) from full
   * absolute file path
   *
   * @param fullPath file's absolute path
   * @return four last path entities joined by OS-separator
   */
  private def getPath(fullPath: String): String =
    fullPath.split(separator).takeRight(4).mkString(separator)


  /**
   * Check if path of some JSON file corresponds with Iglu path extracted
   * from its self-describing info
   *
   * @param jsonFile some existing JSON file with defined path in it
   * @param schemaKey schema key extracted from it
   * @return true if extracted path is equal to FS path
   */
  def equalPath(jsonFile: JsonFile, schemaKey: SchemaKey): Boolean =
    jsonFile.path match {
      case Some(_) =>
        val path = getPath(jsonFile.getKnownPath)
        SchemaKey.fromPath(path) == Some(schemaKey)
      case None => false
    }

  /**
   * Extract self-describing JSON Schema from JSON file
   *
   * @param jsonFile some existing on FS valid JSON file
   * @return self-describing JSON Schema if successful or error message if
   *         file is not Schema or self-describing Schema or has invalid
   *         file path
   */
  def extractSchema(jsonFile: JsonFile): String \/ IgluSchema =
    jsonFile.extractSelfDescribingSchema.disjunction match {
      case \/-(schema) if equalPath(jsonFile, schema.self) => schema.right
      case \/-(schema) => s"Error: JSON Schema [${schema.self.toSchemaUri}] doesn't conform path [${getPath(jsonFile.getKnownPath)}]".left
      case -\/(error) => error.left
    }
}
