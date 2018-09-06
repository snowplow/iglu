package com.snowplowanalytics.iglu.schemaddl

import scala.reflect.runtime.{universe => ru}
import cats.data._
import cats.implicits._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.{ArrayProperty, NumberProperty, ObjectProperty, StringProperty}

// This library
import jsonschema._
import StringProperty._
import com.snowplowanalytics.iglu.schemaddl.jsonschema.properties.CommonProperties._
import ObjectProperty.{ AdditionalProperties, Properties, Required }

import Linter._

/** Schema validation logic */
sealed trait Linter extends Product with Serializable {
  def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit]
  def getName: String = toString
}

object Linter {

  sealed trait Issue extends Product with Serializable {
    /** Linter revealed this issue */
    def linter: Linter

    /** Transform into human-readable message */
    def getMessage: String

    /** Name of the linter revealed this issues */
    def getLinterName: String = linter.getName
  }

  def allLintersMap: Map[String, Linter] =
    sealedDescendants[Linter].map(x => (x.getName, x)).toMap

  case object rootObject extends Linter { self =>
    case object Details extends Issue {
      val linter = self
      def getMessage: String =
        "Root of schema should have type object and contain properties"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] =
      if (jsonPointer == JsonPointer.Root && (schema.properties.isEmpty || !schema.`type`.contains(Type.Object)))
        Details.invalid
      else noIssues
  }

  case object numericMinimumMaximum extends Linter { self =>

    case class Details(min: BigDecimal, max: BigDecimal) extends Issue {
      val linter = self
      def getMessage: String = s"Schema with numeric type has minimum property [$min] greater than maximum [$max]"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minimum, schema.maximum) match {
        case (Some(min), Some(max)) =>
          (max.getAsDecimal >= min.getAsDecimal).or(Details(min.getAsDecimal, max.getAsDecimal))
        case _ => noIssues
      }
  }

  case object stringMinMaxLength extends Linter { self =>
    case class Details(min: BigInt, max: BigInt) extends Issue {
      val linter = self
      def getMessage: String = s"Schema with string type has minLength property [$min] greater than maxLength [$max]"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minLength, schema.maxLength) match {
        case (Some(min), Some(max)) =>
          (max.value >= min.value).or(Details(min.value, max.value))
        case _ => noIssues
      }
  }

  case object stringMaxLengthRange extends Linter { self =>
    case class Details(maximum: BigInt) extends Issue {
      val linter = self
      def getMessage: String =
        s"Schema with string type has maxLength property [$maximum] greater than Redshift VARCHAR(max) 65535"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      if (schema.withType(Type.String)) {
        schema.maxLength match {
          case Some(max) if max.value > 65535 =>
            Details(max.value).invalid
          case _ => noIssues
        }
      }
      else noIssues
  }

  case object arrayMinMaxItems extends Linter { self =>
    case class Details(minimum: BigInt, maximum: BigInt) extends Issue {
      val linter = self
      def getMessage: String =
        s"Schema with array type has minItems property [$minimum] greater than maxItems [$maximum]"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      (schema.minItems, schema.maxItems) match {
        case (Some(min), Some(max)) =>
          (max.value >= min.value).or(Details(min.value, max.value))
        case _ => noIssues
      }
  }

  case object numericProperties extends Linter { self =>
    case class Details(keys: List[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"Numeric properties [${keys.mkString(",")}] require number, integer or absent type"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] = {
      val numberProperties = schema.allProperties.collect {
        case Some(p: NumberProperty) => p
      }
      val fruitless = numberProperties.nonEmpty && (schema.withoutType(Type.Number) && schema.withoutType(Type.Integer))
      (!fruitless).or(Details(numberProperties.map(_.keyName)))
    }
  }

  case object stringProperties extends Linter { self =>
    case class Details(keys: List[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"String properties [${keys.mkString(",")}] require string or absent type"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] = {
      val stringProperties = schema.allProperties.collect {
        case Some(p: StringProperty) => p
      }
      val fruitless = stringProperties.nonEmpty && schema.withoutType(Type.String)
      (!fruitless).or(Details(stringProperties.map(_.keyName)))
    }
  }

  case object arrayProperties extends Linter { self =>
    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"Array properties [${keys.mkString(",")}] require array or absent type"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] = {
      val arrayProperties = schema.allProperties.collect {
        case Some(p: ArrayProperty) => p
      }
      val fruitless = arrayProperties.nonEmpty && schema.withoutType(Type.Array)
      (!fruitless).or(Details(arrayProperties.map(_.keyName).toSet))
    }
  }

  case object objectProperties extends Linter { self =>
    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"Object properties [${keys.mkString(",")}] require object or absent type"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] = {
      val objectProperties = schema.allProperties.collect {
        case Some(p: ObjectProperty) => p
      }
      val fruitless = objectProperties.map(_.keyName).nonEmpty && schema.withoutType(Type.Object)
      (!fruitless).or(Details(objectProperties.map(_.keyName).toSet))
    }
  }

  case object requiredPropertiesExist extends Linter { self =>
    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"Required properties [${keys.mkString(",")}] doesn't exist in properties"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      (schema.additionalProperties, schema.required, schema.properties, schema.patternProperties) match {
        case (Some(AdditionalProperties.AdditionalPropertiesAllowed(false)), Some(Required(required)), Some(Properties(properties)), None) =>
          val allowedKeys = properties.keySet
          val requiredKeys = required.toSet
          val diff = requiredKeys -- allowedKeys
          diff.isEmpty.or(Details(diff))
        case _ => noIssues
      }
  }

  case object unknownFormats extends Linter { self =>
    case class Details(name: String) extends Issue {
      val linter = self
      def getMessage: String =
        s"Unknown format [$name] detected. Known formats are: date-time, date, email, hostname, ipv4, ipv6, uri"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Details, Unit] =
      schema.format match {
        case Some(Format.CustomFormat(format)) =>
          Details(format).invalid
        case _ => noIssues
      }
  }

  case object numericMinMax extends Linter { self =>
    case object Details extends Issue {
      val linter = self
      def getMessage: String = "Schema with numeric type doesn't contain minimum and maximum properties"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] =
      if (schema.withType(Type.Number) || schema.withType(Type.Integer)) {
        (schema.minimum, schema.maximum) match {
          case (Some(_), Some(_)) => noIssues
          case _ => Details.invalid
        }
      }
      else noIssues
  }

  case object stringLength extends Linter { self =>
    case object Details extends Issue {
      val linter = self
      def getMessage: String =
        "Schema with string type doesn't contain maxLength property or other ways to extract max length"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] =
      if (schema.withType(Type.String) && schema.enum.isEmpty && schema.maxLength.isEmpty) {
        if (schema.withFormat(Format.Ipv4Format) || schema.withFormat(Format.Ipv6Format) || schema.withFormat(Format.DateTimeFormat))
          noIssues
        else
          Details.invalid
      } else { noIssues }
  }

  case object optionalNull extends Linter { self =>
    case class Details(keys: Set[String]) extends Issue {
      val linter = self
      def getMessage: String =
        s"Optional fields [${keys.mkString(",")}] don't allow null type"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] =
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

  case object description extends Linter { self =>
    case object Details extends Issue {
      val linter = self
      def getMessage: String = "Schema doesn't contain description property"
    }

    def apply(jsonPointer: JsonPointer, schema: Schema): Validated[Issue, Unit] =
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

  /**
    * Pimp JSON Schema AST with method checking presence of some JSON type
    */
  private[schemaddl] implicit class SchemaOps(val value: Schema) extends AnyVal {
    /** Check if Schema has no specific type *OR* has no type at all */
    def withoutType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Type.Product(types)) => !types.contains(jsonType)
        case Some(t) => t != jsonType
        case None => false            // absent type is ok
      }

    /** Check if Schema has no specific type *OR* has no type at all */
    def withType(jsonType: Type): Boolean =
      value.`type` match {
        case Some(Type.Product(types)) => types.contains(jsonType)
        case Some(t) => t == jsonType
        case None => false            // absent type is ok
      }

    /** Check if Schema has specified format */
    def withFormat(format: Format): Boolean =
      value.format match {
        case Some(f) => format == f
        case None => false
      }
  }

  private def noIssues = ().valid[Nothing]
}
