/*
 * Copyright (c) 2016-2018 Snowplow Analytics Ltd. All rights reserved.
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

import cats.Show
import cats.data._
import cats.implicits._

import scala.reflect.runtime.{universe => ru}

// This library
import Linter._
import properties.{ArrayProperty, NumberProperty, ObjectProperty, StringProperty}
import properties.CommonProperties._
import properties.ObjectProperty._
import properties.StringProperty._

/** Schema validation logic */
sealed trait Linter extends Product with Serializable {
  def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit]
  def getName: String = toString
  def level: Level
}

object Linter {

  sealed trait Level extends Product with Serializable
  object Level {
    case object Info extends Level
    case object Warning extends Level
    case object Error extends Level
  }

  sealed trait Issue extends Product with Serializable {
    /** Linter revealed this issue */
    def linter: Linter

    /** Transform into human-readable message */
    def show: String

    /** Name of the linter revealed this issues */
    def getLinterName: String = linter.getName

    /** To linter-agnostic message */
    def toMessage(pointer: Pointer.SchemaPointer): Message =
      Message(pointer, show, linter.level)
  }

  object Issue {
    implicit val issueShow: Show[Issue] =
      Show.show((i: Issue) => i.show)
  }

  /** Linter-agnostic message */
  final case class Message(jsonPointer: Pointer.SchemaPointer, message: String, level: Linter.Level)

  def allLintersMap: Map[String, Linter] =
    sealedDescendants[Linter].map(x => (x.getName, x)).toMap

  final case object rootObject extends Linter { self =>

    val level: Level = Level.Warning

    case object Details extends Issue {
      val linter = self
      def show: String =
        "At the root level, the schema should have a \"type\" property set to \"object\" and have a \"properties\" property"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] =
      if (jsonPointer == Pointer.Root && (schema.properties.isEmpty || !schema.`type`.contains(Type.Object)))
        Details.invalid
      else noIssues
  }

  final case object numericMinimumMaximum extends Linter { self =>

    val level: Level = Level.Error

    case class Details(min: BigDecimal, max: BigDecimal) extends Issue {
      val linter = self
      def show: String = s"A field with numeric type has a minimum value [$min] greater than the maximum value [$max]"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minimum, schema.maximum) match {
        case (Some(min), Some(max)) =>
          (max.getAsDecimal >= min.getAsDecimal).or(Details(min.getAsDecimal, max.getAsDecimal))
        case _ => noIssues
      }
  }

  final case object stringMinMaxLength extends Linter { self =>

    val level: Level = Level.Error

    case class Details(min: BigInt, max: BigInt) extends Issue {
      val linter = self
      def show: String = s"""A string type with "minLength" and "maxLength" property values has a minimum value [$min] higher than the maximum [$max]"""
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minLength, schema.maxLength) match {
        case (Some(min), Some(max)) =>
          (max.value >= min.value).or(Details(min.value, max.value))
        case _ => noIssues
      }
  }

  final case object stringMaxLengthRange extends Linter { self =>

    val level: Level = Level.Warning

    case class Details(maximum: BigInt) extends Issue {
      val linter = self
      def show: String =
        s"""A string property has a "maxLength" [$maximum] greater than the Redshift VARCHAR maximum of 65535"""
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      if (schema.withType(Type.String)) {
        schema.maxLength match {
          case Some(max) if max.value > 65535 =>
            Details(max.value).invalid
          case _ => noIssues
        }
      }
      else noIssues
  }

  final case object arrayMinMaxItems extends Linter { self =>

    val level: Level = Level.Error

    case class Details(minimum: BigInt, maximum: BigInt) extends Issue {
      val linter = self
      def show: String =
        s"""A field of array type has a "minItems" value [$minimum] with a greater value than the "maxItems" [$maximum]"""
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minItems, schema.maxItems) match {
        case (Some(min), Some(max)) =>
          (max.value >= min.value).or(Details(min.value, max.value))
        case _ => noIssues
      }
  }

  final case object numericProperties extends Linter { self =>

    val level: Level = Level.Error

    case class Details(keys: List[String]) extends Issue {
      val linter = self
      def show: String =
        s"Numeric properties [${keys.mkString(",")}] require either a number, integer or absent values"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] = {
      val numberProperties = schema.allProperties.collect {
        case Some(p: NumberProperty) => p
      }
      val fruitless = numberProperties.nonEmpty && (schema.withoutType(Type.Number) && schema.withoutType(Type.Integer))
      (!fruitless).or(Details(numberProperties.map(_.keyName)))
    }
  }

  final case object stringProperties extends Linter { self =>

    val level: Level = Level.Error

    case class Details(keys: List[String]) extends Issue {
      val linter = self
      def show: String =
        s"String properties [${keys.mkString(",")}] require either string or absent values"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] = {
      val stringProperties = schema.allProperties.collect {
        case Some(p: StringProperty) => p
      }
      val fruitless = stringProperties.nonEmpty && schema.withoutType(Type.String)
      (!fruitless).or(Details(stringProperties.map(_.keyName)))
    }
  }

  final case object arrayProperties extends Linter { self =>

    val level: Level = Level.Error

    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def show: String =
        s"Array properties [${keys.mkString(",")}] require either array or absent values"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] = {
      val arrayProperties = schema.allProperties.collect {
        case Some(p: ArrayProperty) => p
      }
      val fruitless = arrayProperties.nonEmpty && schema.withoutType(Type.Array)
      (!fruitless).or(Details(arrayProperties.map(_.keyName).toSet))
    }
  }

  final case object objectProperties extends Linter { self =>

    val level: Level = Level.Error

    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def show: String =
        s"Object properties [${keys.mkString(",")}] require either object or absent values"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] = {
      val objectProperties = schema.allProperties.collect {
        case Some(p: ObjectProperty) => p
      }
      val fruitless = objectProperties.map(_.keyName).nonEmpty && schema.withoutType(Type.Object)
      (!fruitless).or(Details(objectProperties.map(_.keyName).toSet))
    }
  }

  final case object requiredPropertiesExist extends Linter { self =>

    val level: Level = Level.Error

    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def show: String =
        s"Elements specified as required [${keys.mkString(",")}] don't exist in schema properties"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      (schema.additionalProperties, schema.required, schema.properties, schema.patternProperties) match {
        case (Some(AdditionalProperties.AdditionalPropertiesAllowed(false)), Some(Required(required)), Some(Properties(properties)), None) =>
          val allowedKeys = properties.keySet
          val requiredKeys = required.toSet
          val diff = requiredKeys -- allowedKeys
          diff.isEmpty.or(Details(diff))
        case _ => noIssues
      }
  }

  final case object unknownFormats extends Linter { self =>

    val level: Level = Level.Warning

    case class Details(name: String) extends Issue {
      val linter = self
      def show: String =
        s"Unknown format [$name] detected. Known formats are: date-time, date, email, hostname, ipv4, ipv6 or uri"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Details, Unit] =
      schema.format match {
        case Some(Format.CustomFormat(format)) =>
          Details(format).invalid
        case _ => noIssues
      }
  }

  final case object numericMinMax extends Linter { self =>

    val level: Level = Level.Warning

    case object Details extends Issue {
      val linter = self
      def show: String = "A numeric property should have \"minimum\" and \"maximum\" properties"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] =
      if (schema.withType(Type.Number) || schema.withType(Type.Integer)) {
        (schema.minimum, schema.maximum) match {
          case (Some(_), Some(_)) => noIssues
          case _ => Details.invalid
        }
      }
      else noIssues
  }

  final case object stringLength extends Linter { self =>

    val level: Level = Level.Warning

    case object Details extends Issue {
      val linter = self
      def show: String =
        "A string type in the schema doesn't contain \"maxLength\" or format which is required"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] =
      if (schema.withType(Type.String) && schema.enum.isEmpty && schema.maxLength.isEmpty) {
        schema.format match {
          case Some(Format.CustomFormat(_)) => Details.invalid
          case None =>  Details.invalid
          case Some(_) => noIssues
        }
      } else { noIssues }
  }

  final case object optionalNull extends Linter { self =>

    val level: Level = Level.Info

    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def show: String =
        s"""Use "type: null" to indicate a field as optional for properties ${keys.mkString(",")}"""
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] =
      (schema.required, schema.properties) match {
        case (Some(Required(required)), Some(Properties(properties))) =>
          val allowedKeys = properties.keySet
          val requiredKeys = required.toSet
          val optionalKeys = allowedKeys -- requiredKeys
          val optKeysWithoutTypeNull = for {
            key <- optionalKeys
            if !properties(key).withType(Type.Null)
          } yield key
          optKeysWithoutTypeNull.isEmpty.or(Details(optKeysWithoutTypeNull))
        case _ => noIssues
      }
  }

  final case object description extends Linter { self =>

    val level: Level = Level.Info

    case object Details extends Issue {
      val linter = self
      def show: String = "The schema is missing the \"description\" property"
    }

    def apply(jsonPointer: Pointer.SchemaPointer, schema: Schema): Validated[Issue, Unit] =
      schema.description match {
        case Some(_) => noIssues
        case None => Details.invalid
      }
  }

  private val m = ru.runtimeMirror(getClass.getClassLoader)

  /**
    * Reflection method to get runtime object by compiler's `Symbol`
    * @param desc compiler runtime `Symbol`
    * @return "real" scala case object
    */
  private def getCaseObject(desc: ru.Symbol): Any = {
    val mod = m.staticModule(desc.asClass.fullName)
    m.reflectModule(mod).instance
  }

  /**
    * Get all objects extending some sealed hierarchy
    * @tparam Root some sealed trait with object descendants
    * @return whole set of objects
    */
  def sealedDescendants[Root: ru.TypeTag]: Set[Root] = {
    val symbol = ru.typeOf[Root].typeSymbol
    val internal = symbol.asInstanceOf[scala.reflect.internal.Symbols#Symbol]
    val descendants = if (internal.isSealed)
      Some(internal.sealedDescendants.map(_.asInstanceOf[ru.Symbol]) - symbol)
    else None
    descendants.getOrElse(Set.empty).map(x => getCaseObject(x).asInstanceOf[Root])
  }

  /**
    * Pimp boolean, so it can pipe failure in case of `false`
    */
  private implicit class LintOps(val value: Boolean) extends AnyVal {
    def or[A](message: A): Validated[A, Unit] =
      if (value) ().valid[A] else message.invalid
  }

  private def noIssues = ().valid[Nothing]
}
