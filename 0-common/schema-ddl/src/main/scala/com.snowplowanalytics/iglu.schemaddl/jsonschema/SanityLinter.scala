/*
 * Copyright (c) 2014-2016 Snowplow Analytics Ltd. All rights reserved.
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

import cats.data._
import cats.implicits._

/**
 * Contains Schema validation logic for JSON AST to find nonsense (impossible)
 * JSON Schemas, ie. Schemas which cannot validate ANY value, yet
 * syntactically correct.
 * This doesn't have logic to validate accordance to JSON Schema specs such as
 * non-empty `required` or numeric `maximum`. Separate validator should be
 * used for that.
 *
 * @see https://github.com/snowplow/iglu/issues/164
 */
object SanityLinter {

  type Report = Map[JsonPointer, NonEmptyList[Linter.Issue]]

  /**
    *
    * Main working function, traversing JSON Schema
    * It lints all properties on current level, then tries to extract all
    * subschemas from properties like `items`, `additionalItems` etc and
    * recursively lint them as well
    *
    * @param schema parsed JSON AST
    * @param linters of linters to be used
    * @return non-empty list of summed failures (all, including nested) or
    *         unit in case of success
    */
  def lint(schema: Schema, linters: List[Linter]): Report =
    Schema.traverse(schema, validate(linters))
      .runS(ValidationState.empty)
      .value.toMap

  /** Get list of linters from their names or list of unknown names */
  def getLinters(names: List[String]): Either[NonEmptyList[String], List[Linter]] =
    names
      .map(name => (name, Linter.allLintersMap.get(name)))
      .traverse[ValidatedNel[String, ?], Linter] {
      case (_, Some(linter)) => linter.validNel
      case (name, None) => name.invalidNel
    }.toEither

  private def validate(linters: List[Linter])(jsonPointer: JsonPointer, schema: Schema): State[ValidationState, Unit] = {
    val results = linters
      .traverse[ValidatedNel[Linter.Issue, ?], Unit](linter => linter(jsonPointer, schema).toValidatedNel)
    results match {
      case Validated.Invalid(errors) =>
        State.modify[ValidationState] { state =>
          state.add((jsonPointer, errors))
        }
      case Validated.Valid(_) =>
        State.pure(())
    }
  }

  private case class ValidationState(issues: List[(JsonPointer, NonEmptyList[Linter.Issue])]) {
    def add(recent: (JsonPointer, NonEmptyList[Linter.Issue])): ValidationState =
      ValidationState(recent :: issues)

    def toMap: Map[JsonPointer, NonEmptyList[Linter.Issue]] = issues.toMap
  }

  private object ValidationState {
    val empty = ValidationState(List.empty)
  }
}
