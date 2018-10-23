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

// cats
import cats.data.{ Validated, NonEmptyList }
import cats.implicits._

// Java
import java.io.File

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{ Schema, Linter }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SanityLinter.{ lint, Report => LinterReport }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SelfSyntaxChecker
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.implicits._

// This library
import GenerateCommand.{Result, Errors, VersionSuccess, Warnings}
import FileUtils.{ getJsonFilesStream, JsonFile, filterJsonSchemas }
import Utils.extractSchema

case class LintCommand(inputDir: File, skipWarnings: Boolean, linters: List[Linter]) extends Command.CtlCommand {
  import LintCommand._

  /**
   * Primary method running command logic
   */
  def process(): Unit = {
    val jsons = getJsonFilesStream(inputDir, Some(filterJsonSchemas))

    val (failures, validatedJsons) = jsons.toList.separate
    if (failures.nonEmpty) {
      println("JSON Parsing errors:")
      println(failures.mkString("\n"))
      sys.exit(1)
    }

    // lint schema versions
    val stubFile: File = new File(inputDir.getAbsolutePath)
    val stubCommand = GenerateCommand(stubFile, stubFile)
    val (_, schemas) = validatedJsons.map(_.extractSelfDescribingSchema).separate
    val schemaVerValidation: Result = stubCommand.validateSchemaVersions(schemas)

    val reports = jsons.map { file => file.map(check).fold(Failure.apply, x => x) }

    // strip GenerateCommand related parts off & prepare LintCommand messages & create a Total per schema version check
    schemaVerValidation match {
      case Warnings(lst) =>
        lst.map(w => "WARNING" + w.stripPrefix("Warning")).foreach(println)
        reports.foldLeft(Total(0, 0, 0, lst.size))((acc, cur) => acc.add(cur)).exit()
      case Errors(lst) =>
        lst.map(e => "FAILURE" +
              e.stripPrefix("Error").stripSuffix(" Use --force to switch off schema version check.")).foreach(println)
        reports.foldLeft(Total(0, 0, lst.size, 0))((acc, cur) => acc.add(cur)).exit()
      case VersionSuccess(_) =>
        reports.foldLeft(Total(0, 0, 0, 0))((acc, cur) => acc.add(cur)).exit()
    }
  }

  /**
   * Perform syntax check and sanity lint
   *
   * @param jsonFile valid JSON file, supposed to contain JSON Schema
   * @return [[Report]] ADT, indicating successful lint or containing errors
   */
  def check(jsonFile: JsonFile): Report = {
    val pathCheck = extractSchema(jsonFile).void.toValidatedNel
    val syntaxCheck = SelfSyntaxChecker.validateSchema(jsonFile.content, skipWarnings).leftMap(_.map(_.message))
    val lintCheck = Schema.parse(jsonFile.content).map { schema => lint(schema, linters) match {
      case e if e == Map.empty => ().validNel[String]
      case report => NonEmptyList.fromListUnsafe(prettifyReport(report)).invalid[Unit]
    } } getOrElse NonEmptyList.of("Doesn't contain JSON Schema").invalid[Unit]

    val fullValidation = syntaxCheck.combine(pathCheck).combine(lintCheck)

    fullValidation match {
      case Validated.Valid(_) =>
        Success(jsonFile.getKnownPath)
      case Validated.Invalid(issues) =>
        SchemaFailure(jsonFile.getKnownPath, issues.toList)
    }
  }
}

object LintCommand {

  /** All lintings that user can skip */
  val OptionalChecks: List[String] =
    Linter.allLintersMap.values.filter(l => l.level != Linter.Level.Error).map(_.getName).toList

  /**
   * End-of-the-world class, containing info about success/failure of execution
   *
   * @param successes number of successfully validated schemas
   * @param failedSchemas number of schemas with errors
   */
  case class Total(successes: Int, failedSchemas: Int, totalFailures: Int, totalWarnings: Int) {
    /**
     * Exit from app with error status if invalid schemas were found
     */
    def exit(): Unit = {
      println(s"TOTAL: $successes Schemas were successfully validated")
      println(s"TOTAL: $failedSchemas invalid Schemas were encountered")
      println(s"TOTAL: $totalFailures errors were encountered")
      println(s"TOTAL: $totalWarnings warnings were encountered")

      if (failedSchemas + totalFailures > 0) sys.exit(1)
      else ()
    }

    /**
     * Append and print report for another schema
     *
     * @param report schema processing result
     * @return modified total object
     */
    def add(report: Report): Total = report match {
      case s: Success =>
        println(s"SUCCESS: ${s.asString}\n")
        copy(successes = successes + 1)
      case f: Failure =>
        println(s"FAILURE: ${f.asString}\n")
        copy(failedSchemas = failedSchemas + 1)
      case f: SchemaFailure =>
        println(s"FAILURE: ${f.asString}\n")
        copy(failedSchemas = failedSchemas + 1, totalFailures = totalFailures + f.errors.size)
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
    def asString = s"Schema [$filePath] contains following errors: \n${formatErrors(errors)}"

    def formatErrors(errors: List[String]): String =
      errors.zipWithIndex.map { case (e, i) => s"${i + 1}. $e" }.mkString("\n")
  }

  case class Success(filePath: String) extends Report {
    def asString = s"Schema [$filePath] is successfully validated"
  }

  /**
    * Validates if user provided --skip-checks with a valid string
    * @param lintersString command line input for --skip-checks
    * @return Either error concatenated error messages or valid list of linters
    */
  def skipLinters(lintersString: String): Either[String, List[Linter]] =
    validateOptionalLinters(lintersString).map { toSkip =>
      Linter.allLintersMap.filterKeys(linter => !toSkip.contains(linter)).values.toList
    }

  /**
    * Validates if user provided --skip-checks with a valid string
    * @param lintersString command line input for --skip-checks
    * @return Either error concatenated error messages or valid list of linters
    */
  def includeLinters(lintersString: String): Either[String, List[Linter]] =
    validateOptionalLinters(lintersString).map { toInclude =>
      Linter.allLintersMap.filterKeys(toInclude.contains).values.toList
    }

  /** Check that comma-separated list passed by --skip-checks is list of optional linters */
  def validateOptionalLinters(string: String): Either[String, List[String]] = {
    val linters = string.split(',').toList
    val invalidLinters = linters.filterNot(Linter.allLintersMap.isDefinedAt)

    val knownLinters: Either[String, List[String]] = invalidLinters match {
      case Nil => Right(linters)
      case invalid => Left(s"Unknown linters [${invalid.mkString(",")}] ")
    }

    for {
      list <- knownLinters
      _ <- list.filterNot(linter => OptionalChecks.contains(linter)) match {
        case Nil => Right(())
        case nonOptional => Left(s"Unknown linters [${nonOptional.mkString(", ")}]")
      }
    } yield list
  }

  def prettifyReport(report: LinterReport): List[String] = {
    // Regroup issues by linter
    val groupedReport = report
      .flatMap { case (pointer, issues) => issues.toList.map(issue => (pointer, issue)) }
      .groupBy { case (_, issue) => issue.linter }
      .map { case (linter, issues) => (linter, issues.toList) }

    val reports = groupedReport.map { case (linter, issues) =>
      val longestPointer = (issues.map { case (k, _) => k.show.length } maximumOption).getOrElse(0)
      val sortedIssues = issues.sortBy { case (k, _) => (k.show, k.show.length) }
      val skipMessage = s" (add --skip-checks ${linter.getName} to omit this check)"
      def leftPad(str: String): String = str ++ ("\t" * (longestPointer - str.length))
      def getMessage(issue: Linter.Issue): String = if (issue.productArity == 0) "" else issue.show

      linter match {
        case Linter.rootObject => "Root of schema should have type object and contain properties"
        case Linter.numericMinimumMaximum => s"Following numeric properties have invalid boundaries:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.stringMinMaxLength => s"Following string properties have invalid length:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.stringMaxLengthRange => s"Following string properties have too big maxLength$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.arrayMinMaxItems => s"Following array properties have invalid boundaries:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.numericProperties => s"Following numeric properties are missing explicit type:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.stringProperties => s"Following string properties are missing explicit type:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.arrayProperties => s"Following array properties are missing explicit type:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.objectProperties => s"Following object properties are missing explicit type:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.requiredPropertiesExist => s"Following required properties are unknown:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.unknownFormats => s"Following formats are unknown$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.numericMinMax => s"Following numeric properties are unbounded$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, _) => s" - ${leftPad(pointer.show)}" } mkString "\n")
        case Linter.stringLength => s"Following string properties provide no clues about maximum length$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, _) => s" - ${leftPad(pointer.show)}" } mkString "\n")
        case Linter.optionalNull => s"Following optional properties don't allow null type$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, issue) => s" - ${leftPad(pointer.show)} ${getMessage(issue)}" } mkString "\n")
        case Linter.description => s"Following properties don't include description$skipMessage:\n" ++
          (sortedIssues.map { case (pointer, _) => s" - ${leftPad(pointer.show)}" } mkString "\n")
      }

    }
    reports.toList
  }
}
