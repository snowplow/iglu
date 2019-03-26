package com.snowplowanalytics.iglu.schemaddl

import org.specs2.Specification
import io.circe.literal._
import SpecHelpers._
import com.snowplowanalytics.iglu.schemaddl.Core.VersionPoint

class SchemaDiffSpec extends Specification { def is = s2"""
  required point check recognizes added required property $e1
  required point check skips added optional (via enum) property $e2
  required point check recognizes changed property being made required $e3
  required point check skips changed optional -> optional property $e4
  getPointer identifies type widening as a revision $e5
  getPointer identifies constraint change as a revision $e6
  getPointer identifies added optional property AND constraint change as a revision $e7
  getPointer identifies added optional property as an addition $e8
  """

  def e1 = {
    val diff = SchemaDiff.empty.copy(added = Set(
      "/properties/foo".jsonPointer -> json"""{"type": "string"}""".schema
    ))

    SchemaDiff.required(diff) must beTrue
  }

  def e2 = {
    val diff = SchemaDiff.empty.copy(added = Set(
      "/properties/foo".jsonPointer -> json"""{"type": "string", "enum": ["foo", null]}""".schema
    ))

    SchemaDiff.required(diff) must beFalse
  }


  def e3 = {
    val modified = SchemaDiff.Modified(
      "/properties/foo".jsonPointer,
      json"""{"type": "string", "enum": ["foo", null]}""".schema,
      json"""{"type": "string"}""".schema)

    val diff = SchemaDiff.empty.copy(modified = Set(modified))

    SchemaDiff.required(diff) must beTrue
  }

  def e4 = {
    val modified = SchemaDiff.Modified(
      "/properties/foo".jsonPointer,
      json"""{"type": "string"}""".schema,
      json"""{"type": ["string", "integer"]}""".schema)

    val diff = SchemaDiff.empty.copy(modified = Set(modified))

    SchemaDiff.required(diff) must beFalse
  }

  def e5 = {
    val modified = SchemaDiff.Modified(
      "/properties/foo".jsonPointer,
      json"""{"type": "string"}""".schema,
      json"""{"type": ["string", "integer"]}""".schema)

    val diff = SchemaDiff.empty.copy(modified = Set(modified))

    SchemaDiff.getPointer(diff) must beSome(VersionPoint.Revision)
  }

  def e6 = {
    val modified = SchemaDiff.Modified(
      "/properties/foo".jsonPointer,
      json"""{"type": "string", "maxLength": 10}""".schema,
      json"""{"type": "string", "maxLength": 12}""".schema)

    val diff = SchemaDiff.empty.copy(modified = Set(modified))

    SchemaDiff.getPointer(diff) must beSome(VersionPoint.Revision)
  }

  def e7 = {
    val addedProps = "/properties/bar".jsonPointer -> json"""{"type": ["string", "null"]}""".schema

    val modified = SchemaDiff.Modified(
      "/properties/foo".jsonPointer,
      json"""{"type": "string", "maxLength": 10}""".schema,
      json"""{"type": "string", "maxLength": 12}""".schema)

    val diff = SchemaDiff.empty.copy(added = Set(addedProps), modified = Set(modified))

    SchemaDiff.getPointer(diff) must beSome(VersionPoint.Revision)
  }

  def e8 = {
    val addedProps = "/properties/bar".jsonPointer -> json"""{"type": ["string", "null"]}""".schema
    val diff = SchemaDiff.empty.copy(added = Set(addedProps))

    SchemaDiff.getPointer(diff) must beSome(VersionPoint.Addition)
  }
}
