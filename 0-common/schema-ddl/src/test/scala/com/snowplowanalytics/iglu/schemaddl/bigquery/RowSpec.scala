package com.snowplowanalytics.iglu.schemaddl.bigquery

import io.circe._
import io.circe.literal._

import org.specs2.matcher.ValidatedMatchers._

class RowSpec extends org.specs2.Specification { def is = s2"""
  castValue transforms any primitive value $e1
  castValue transforms object with matching primitive fields $e2
  castValue transforms object with missing nullable field $e3
  cast strips away undefined properties $e4
  cast transforms deeply nested object $e5
  cast does not stringify null when mode is Nullable $e6
  cast turns any unexpected type into string, when schema type is string $e7
  castRepeated eleminates nulls $e8
  """

  import Row._

  def e1 = {
    val string = castValue(Type.String)(json""""foo"""") must beValid(Primitive("foo"))
    val int = castValue(Type.Integer)(json"-43") must beValid(Primitive(-43))
    val bool = castValue(Type.Boolean)(Json.fromBoolean(false)) must beValid(Primitive(false))
    string and int and bool
  }

  def e2 = {
    val inputJson = json"""{"foo": 42, "bar": true}"""
    val inputField = Type.Record(List(
      Field("foo", Type.Integer, Mode.Nullable),
      Field("bar", Type.Boolean, Mode.Required)))
    val expected = Record(List(("foo", Primitive(42)), ("bar", Primitive(true))))
    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e3 = {
    val inputJson = json"""{"bar": true}"""
    val inputField = Type.Record(List(
      Field("foo", Type.Integer, Mode.Nullable),
      Field("bar", Type.Boolean, Mode.Required)))
    val expected = Record(List(("foo", Null), ("bar", Primitive(true))))
    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e4 = {
    val inputJson =
      json"""{
        "someBool": true,
        "repeatedInt": [1,5,2],
        "undefined": 42
      }"""

    val inputField = Type.Record(List(
      Field("someBool", Type.Boolean, Mode.Required),
      Field("repeatedInt", Type.Integer, Mode.Repeated)))

    val expected = Record(List(("someBool", Primitive(true)), ("repeatedInt", Repeated(List(Primitive(1), Primitive(5), Primitive(2))))))
    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e5 = {
    val inputJson =
      json"""{
        "someBool": true,
        "nested": {
          "str": "foo bar",
          "int": 3,
          "deep": { "str": "foo" },
          "arr": [{"a": "b"}, {"a": "d", "b": "c"}]
        }
      }"""

    val inputField = Type.Record(List(
      Field("someBool", Type.Boolean, Mode.Required),
      Field("nested", Type.Record(List(
        Field("str", Type.String, Mode.Required),
        Field("int", Type.Integer, Mode.Nullable),
        Field("deep", Type.Record(List(Field("str", Type.String, Mode.Nullable))), Mode.Required),
        Field("arr", Type.Record(List(Field("a", Type.String, Mode.Required))), Mode.Repeated)
      )), Mode.Nullable)
    ))

    val expected = Record(List(
      ("someBool", Primitive(true)),
      ("nested", Record(List(
        ("str", Primitive("foo bar")),
        ("int", Primitive(3)),
        ("deep", Record(List(("str", Primitive("foo"))))),
        ("arr", Repeated(List(Record(List(("a", Primitive("b")))), Record(List(("a", Primitive("d")))))))
      )))
    ))

    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e6 = {
    val inputJson =
      json"""{
        "optional": null
      }"""

    val inputField = Type.Record(List(Field("optional", Type.String, Mode.Nullable)))

    val expected = Record(List(("optional", Null)))
    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e7 = {
    val inputJson =
      json"""{
        "unionType": ["this", "is", "fallback", "strategy"]
      }"""

    val inputField = Type.Record(List(Field("unionType", Type.String, Mode.Nullable)))

    val expected = Record(List(("unionType", Primitive("""["this","is","fallback","strategy"]"""))))
    castValue(inputField)(inputJson) must beValid(expected)
  }

  def e8 = {    // TODO: check if BQ actually allows it
    val inputJson =
      json"""["this", "has", "no", null, "at all", null]"""

    val inputField = Type.String

    val expected = Repeated(List(Primitive("this"), Primitive("has"), Primitive("no"), Primitive("at all")))
    castRepeated(inputField)(inputJson) must beValid(expected)
  }
}
