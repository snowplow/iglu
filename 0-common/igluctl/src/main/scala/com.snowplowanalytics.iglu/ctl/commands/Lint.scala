/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
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
package commands

import java.nio.file.Path

import cats.data.{EitherT, NonEmptyList, Ior, IorNel, EitherNel}
import cats.implicits._

import com.snowplowanalytics.iglu.core.SelfDescribingSchema
import com.snowplowanalytics.iglu.schemaddl.jsonschema.json4s.implicits._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.SanityLinter.{ lint, Report => LinterReport }
import com.snowplowanalytics.iglu.schemaddl.jsonschema.{Linter, Schema, SelfSyntaxChecker}

import org.json4s.JsonAST.JValue

import com.snowplowanalytics.iglu.ctl.Common.Error

object Lint {

  type Report = Either[SchemaFailure, String]

  /** All lintings that user can skip */
  val OptionalChecks: List[String] =
    Linter.allLintersMap.values.filter(l => l.level != Linter.Level.Error).map(_.getName).toList

  /**
    * Primary method running command logic
    * @param input path to a directory or single schema
    * @param lintersToSkip list of linters that should *NOT* be used
    * @param skipWarnings whether metaschema's warning should be considered (e.g. unknown format)
    *
    * Read: filter out, but do not short-circuit the process in case of
    * inaccessible file, invalid JSON, invalid Schema. Do not load into memory
    * Lint: do not filter out, do not short-circuit just collect warnings
    * Return: amount of warnings, amount of files
    */
  def process(input: Path, lintersToSkip: List[Linter], skipWarnings: Boolean): Result = {
    val lintersToUse = skipLinters(lintersToSkip)
    val result = for {
      consistentSchemas <- File.readSchemas(input)
      reports  = consistentSchemas.map(schemas => schemas.map(_.content).map(check(lintersToUse, skipWarnings)))
    } yield prepareReports(reports)
    EitherT(result)
  }

  /**
    *
    * @param result Errors representing, critical failures, e.g. invalid JSON
    *               and Reports representing linting result
    * @return either non-empty list of errors or possibly empty list of stdout lines
    */
  def prepareReports(result: IorNel[Common.Error, NonEmptyList[Report]]): EitherNel[Error, List[String]] = {
    val valid: String => String = path => s"OK: $path"
    val invalid: SchemaFailure => String = failure => s"FAILURE: ${failure.asString}"
    val stats = result.bimap(
      e => List(s"TOTAL: ${e.length} files corrupted"),
      r => List(s"TOTAL: ${r.filter(_.isRight).length} valid schemas", s"TOTAL: ${r.filter(_.isLeft).length} schemas didn't pass validation")
    ).merge
    val messages = result match {
      case Ior.Left(errors) => Left(errors)
      case Ior.Both(warnings, reps) =>
        Left(warnings ::: reps.map(_.fold(invalid, valid)).map(e => Error.Message(e)))
      case Ior.Right(reps) =>
        if (reps.exists(_.isLeft))
          Left(reps.map(_.fold(invalid, valid)).map(e => Error.Message(e)))
        else
          Right(reps.map(_.fold(invalid, valid)).toList)
    }
    messages.map(m => m ::: stats).leftMap(m => m.concat(stats.map(e => Error.Message(e))))
  }

  /**
    * Perform syntax check and sanity lint
    * @param linters list of optional linters to include
    * @param schema a JSON schema with validated correct underlying FS path
    * @return [[Report]] ADT, indicating successful lint or containing errors
    */
  def check(linters: List[Linter], skipWarnings: Boolean)(schema: SelfDescribingSchema[JValue]): Either[SchemaFailure, String] = {
    val syntaxCheck = SelfSyntaxChecker.validateSchema(schema.schema, skipWarnings).leftMap(_.map(_.message))
    val lintCheck = Schema.parse(schema.schema).map { schema =>
      lint(schema, linters) match {
        case report if report.isEmpty => ().validNel[String]
        case report => NonEmptyList.fromListUnsafe(prettifyReport(report)).invalid[Unit]
      }
    } getOrElse NonEmptyList.of("Doesn't contain JSON Schema").invalid[Unit]

    val fullValidation = syntaxCheck.combine(lintCheck)

    fullValidation
      .toEither
      .as(schema.self.schemaKey.toPath)
      .leftMap(issues => SchemaFailure(schema.self.schemaKey.toPath, issues))
  }

  case class SchemaFailure(filePath: String, issues: NonEmptyList[String]) {
    def asString = s"Schema [$filePath] contains $count following issues: \n${formatErrors(issues)}"

    def formatErrors(errors: NonEmptyList[String]): String =
      errors.zipWithIndex.map { case (e, i) => s"${i + 1}. $e" }.mkString_("", "\n", "")

    val count = issues.length
  }

  /** Get only linters not listed in `optionalLinters` */
  def skipLinters(toSkip: List[Linter]): List[Linter] =
    Linter.allLintersMap
      .filterNot { case (_, linter) => toSkip.contains(linter) }
      .values
      .toList

  /**
    * Validates if user provided --skip-checks with a valid string
    * @param lintersString command line input for --skip-checks
    * @return Either error concatenated error messages or valid list of linters
    */
  def parseOptionalLinters(lintersString: String): Either[Error, List[Linter]] =
    validateOptionalLinters(lintersString).map { toInclude =>
      Linter.allLintersMap.filterKeys(toInclude.contains).values.toList
    }.leftMap(error => Error.ConfigParseError(error))

  /** Check that comma-separated list passed by --skip-checks is list of optional linters */
  def validateOptionalLinters(string: String): Either[String, List[String]] = {
    val linters = string.split(',').toList
    val invalidLinters = linters.filterNot(Linter.allLintersMap.isDefinedAt)

    val knownLinters: Either[String, List[String]] = invalidLinters match {
      case Nil => Right(linters)
      case invalid => Left(s"unknown linters [${invalid.mkString(",")}] ")
    }

    for {
      list <- knownLinters
      _ <- list.filterNot(linter => OptionalChecks.contains(linter)) match {
        case Nil => Right(())
        case nonOptional => Left(s"non-skippable linters [${nonOptional.mkString(", ")}]")
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
      def leftPad(str: String): String = str ++ (" " * (longestPointer - str.length)) ++ "\t"
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
