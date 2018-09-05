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
package com.snowplowanalytics.iglu.schemaddl

// cats
import cats.data.Validated
import cats.syntax.validated._

// Scala
import scala.annotation.tailrec
import scala.collection.immutable.ListMap

// json4s
import org.json4s._

/**
 * Flat schema container. Contains self-describing properties in ``self``
 * and all primitive types as ordered flatten map in ``elems``
 *
 * @param elems The ordered map of every primitive type in schema
 *              and it's unordered map of properties
 */
case class FlatSchema(elems: ListMap[String, Map[String, String]], required: Set[String] = Set.empty[String])

/**
 * Flattens a JsonSchema into Strings representing the path to a field.
 * This will be linked with a Map of attributes that the field possesses.
 */
object FlatSchema {
  // Needed for json4s default extraction formats
  implicit val formats = DefaultFormats

  /**
   * Helper class used to accumulate info about schema while processing schema
   * in ``processProperties``. Contains information about JSON object
   * First-level of ``elems`` map keys contains properties of object (such as user_id)
   * Second-level of ``elems`` contains properties of
   *
   * @param elems map of JSON Schema keys and it's properties
   * @param required set of required keys with full dotted path
   */
  private[schemaddl] case class SubSchema(elems: Map[String, Map[String, String]], required: Set[String])

  /**
   * Helper classes to extract info from JSON Schema's JValue
   */
  private[schemaddl] sealed trait Info

  /**
   * Helper class for all primitive types
   */
  private[schemaddl] case object PrimitiveInfo extends Info

  /**
   * Helper class for arrays
   */
  private[schemaddl] case object ArrayInfo extends Info

  /**
   * Helper class for storing information extracted from JSON Schema's object
   *
   * @param properties list of all object properties with it's names
   * @param required set of fields listed in required property
   */
  private[schemaddl] case class ObjectInfo(properties: List[JField], required: Set[String]) extends Info

  /**
   * Helper object extracted from JSON Schema's object which should be
   * represented as VARCHAR(4096) in future
   */
  private[schemaddl] case object FlattenObjectInfo extends Info

  /**
   * Flattens a JsonSchema into a usable Map of Strings and Attributes.
   * - Will grab the first properties list and begin the recursive function
   * - Will then return the validated object containing paths, attributes, etc
   *
   * @param jSchema the JSON Schema which we will process
   * @param splitProduct whether we need to split product types to different keys
   * @return a validated map of keys and attributes or a failure string
   */
  def flattenJsonSchema(jSchema: JValue, splitProduct: Boolean): Validated[String, FlatSchema] = {
    // Match against the Schema and check that it is properly formed: i.e. wrapped in { ... }
    jSchema match {
      case JObject(list) =>
        // Analyze the base level of the schema
        getElemInfo(list) match {
          case Validated.Valid(ObjectInfo(properties, required)) =>
            processProperties(properties, requiredKeys = required, requiredAccum = required) match {
              case Validated.Valid(subSchema) =>
                val elems = if (splitProduct) splitProductTypes(subSchema.elems) else subSchema.elems
                FlatSchema(getOrderedMap(elems), subSchema.required).valid
              case Validated.Invalid(str) => str.invalid
            }
          case Validated.Valid(FlattenObjectInfo) =>
            FlatSchema(getOrderedMap(Map.empty[String, Map[String, String]]), Set.empty[String]).valid
          case Validated.Invalid(str) => str.invalid
          case _ => ("Error: Function - 'flattenJsonSchema' - " +
                     "JsonSchema does not begin with an 'object' & 'properties'").invalid
        }
      case _ => s"Error: Function - 'flattenJsonSchema' - Invalid Schema passed to flattener".invalid
    }
  }

  /**
   * Split every product type into several primitive types.
   * Preserves nulls and other properties in all output types
   *
   * eg. {"oneKey": {"type": "string,number,null"}} =>
   *     {"oneKey_string": {"type": "string,null"},
   *  "    oneKey_number": {"type": "number,null"}}
   *
   * @param schema map of schema keys to JSON Schema properties
   * @return updated map where product types are splitted
   */
  def splitProductTypes(schema: Map[String, Map[String, String]]): Map[String, Map[String, String]] = {
    val extractedProperties: Iterable[Map[String, Map[String, String]]] = for {
      (k, v) <- schema
    } yield {
        v.get("type") match {
          case Some(types) if isProductType(types) =>
            extractTypesFromProductType(types).map(t => k + "_" + t -> updateType(v, t)).toMap
          case _ => Map(k -> v)
        }
      }
    extractedProperties.reduce(_ ++ _)
  }

  /**
   * Processes the properties of an object. This can be for 'n' amount of
   * attributes. The list of properties can also contain other objects with
   * nested properties.
   *
   * @param propertyList The list of properties of a JsonSchema 'object'
   * @param accum The accumulated map of keys and attributes
   * @param accumKey The key that is added to for each  nested 'object' level
   * @return a validated map of keys and attributes or a failure string
   */
  private[schemaddl] def processProperties(
      propertyList: List[JField],
      accum: Map[String, Map[String, String]] = Map(),
      accumKey: String = "",
      requiredKeys: Set[String] = Set.empty,
      requiredAccum: Set[String] = Set.empty)
  : Validated[String, SubSchema] = {

    propertyList match {
      case x :: xs => {
        val res: Validated[String, SubSchema] = x match {
          case (key, JObject(list)) =>
            getElemInfo(list) match {
              case Validated.Valid(ObjectInfo(properties, required)) =>
                val currentLevelRequired = if (requiredAccum.contains(key)) { required } else { Set.empty[String] }
                val keys = properties.map(_._1).filter(currentLevelRequired.contains).map(accumKey + key + "." + _)
                processProperties(properties, Map(), accumKey + key + ".", keys.toSet, required)
              case Validated.Valid(FlattenObjectInfo) =>
                SubSchema(Map(accumKey + key -> Map("type" -> "string")), Set.empty[String]).valid
              case Validated.Valid(ArrayInfo) =>
                SubSchema(Map(accumKey + key -> Map("type" -> "array")), Set.empty[String]).valid
              case Validated.Valid(_) => processAttributes(list) match {
                case Validated.Valid(attr) => SubSchema(Map(accumKey + key -> attr), Set.empty[String]).valid
                case Validated.Invalid(str)  => str.invalid
              }
              case Validated.Invalid(str) => str.invalid
            }
          case _ => s"Error: Function - 'processProperties' - Invalid List Tuple2 Encountered".invalid
        }

        res match {
          case Validated.Valid(goodRes) =>
            processProperties(xs, accum ++ goodRes.elems, accumKey, requiredKeys ++ goodRes.required, requiredAccum)
          case Validated.Invalid(badRes) => badRes.invalid
        }
      }
      case Nil => SubSchema(accum, requiredKeys).valid
    }
  }

  /**
   * Processes the attributes of an objects list element.
   * This function is aimed at primitive types: i.e. cannot be of type 'object'
   * or 'array'.
   *
   * @param attributes The list of attributes that an element has
   * @param accum The accumulated Map of String -> String attributes
   * @return a validated map of attributes or a failure string
   */
  @tailrec
  private[schemaddl] def processAttributes(
      attributes: List[JField],
      accum: Map[String, String] = Map())
  : Validated[String, Map[String, String]] = {

    attributes match {
      case x :: xs =>
        x match {
          case (key, JArray(value))   =>
            stringifyArray(value) match {
              case Validated.Valid(strs) => processAttributes(xs, accum ++ Map(key -> strs))
              case Validated.Invalid(str)  => str.invalid
            }
          case (key, JBool(value))    => processAttributes(xs, accum ++ Map(key -> value.toString))
          case (key, JInt(value))     => processAttributes(xs, accum ++ Map(key -> value.toString))
          case (key, JDecimal(value)) => processAttributes(xs, accum ++ Map(key -> value.toString))
          case (key, JDouble(value))  => processAttributes(xs, accum ++ Map(key -> value.toString))
          case (key, JNull)           => processAttributes(xs, accum ++ Map(key -> "null"))
          case (key, JString(value))  => processAttributes(xs, accum ++ Map(key -> value))
          case _ => s"Error: Function - 'processAttributes' - Invalid JValue found".invalid
        }
      case Nil => accum.valid
    }
  }

  /**
   * Takes a list of values (currently only numbers and strings) and converts
   * them into a single string delimited by a comma
   *
   * List(JInt(3), JNull, JString(hello)) -> 3,null,hello
   *
   * @param list The list of values to be combined
   * @param accum The accumulated String from the list
   * @param delim The deliminator to be used between strings
   * @return A validated String containing all entities of the list that was
   *         passed or a failure string
   */
  private[schemaddl] def stringifyArray(
      list: List[JValue],
      accum: String = "",
      delim: String = ",")
  : Validated[String, String] = {

    list match {
      case x :: xs =>
        x match {
          case JString(str) => stringifyArray(xs, accum + delim + str)
          case JInt(i)      => stringifyArray(xs, accum + delim + i.toString)
          case JDecimal(d)  => stringifyArray(xs, accum + delim + d.toString)
          case JDouble(d)   => stringifyArray(xs, accum + delim + d.toString)
          case JNull        => stringifyArray(xs, accum + delim + "null")
          case _            => s"Error: Function - 'processList' - Invalid JValue: $x in list".invalid
        }
      case Nil => accum.drop(1).valid
    }
  }

  /**
   * Get all required keys taken from JSON Schema object's "required" key
   *
   * @param jObject JSON object containing JSON Schema
   * @return validated list of required fields
   */
  private[schemaddl] def getRequiredProperties(jObject: Map[String, JValue]): Validated[String, List[String]] = {
    // Helper function, validates each element as string
    def JStringToString(array: List[JValue]): Validated[String, List[String]] =
      array.foldLeft(Nil.valid: Validated[String, List[String]]) { (acc, str) =>
        acc match {
          case Validated.Valid(l) => str match {
            case JString(s) => (s :: l).valid
            case _ => "required property must contain only strings".invalid
          }
          case Validated.Invalid(f) => f.invalid
        }
      }
    jObject.get("required") match {
      case Some(required) => required match {
        case JArray(list) => JStringToString(list)
        case _ => "required property must contain array of keys".invalid
      }
      case None => Nil.valid
    }
  }

  /**
   * Returns information about a single list element:
   * - What 'core' type the element is (object,array,other)
   * - If it is an 'object' returns the properties list for processing
   *
   * @param maybeAttrList The list of attributes which need to be analysed to
   *                      determine what to do with them
   * @return a map which contains a string illustrating what needs to be done
   *         with the element
   */
  private[schemaddl] def getElemInfo(maybeAttrList: List[JField]): Validated[String, Info] = {
    val objectMap = maybeAttrList.toMap
    objectMap.get("type") match {
      case Some(types: JValue) =>
        getElemType(types) match {
          case Validated.Valid(elemType) =>
            elemType match {
              case "object" =>
                // TODO: probably won't work on complex product types
                objectMap.get("properties") match {
                  case Some(JObject(props)) =>
                    val requiredFields = getRequiredProperties(objectMap)
                    requiredFields match {
                      case Validated.Valid(required) => ObjectInfo(props, required.toSet).valid
                      case Validated.Invalid(str)    => str.invalid
                    }
                  case _ => FlattenObjectInfo.valid
                }
              case "array"  => ArrayInfo.valid
              case _        => PrimitiveInfo.valid // Pass back a successful empty Map for a normal entry (Should come up with something better...)
            }
          case Validated.Invalid(str) => str.invalid
        }
      case None if objectMap.get("enum").isDefined => PrimitiveInfo.valid
      case _ => FlattenObjectInfo.valid
    }
  }

  /**
   * Process the type field of an element; can be either a String or
   * an array of Strings.
   *
   * TODO: Add validation for odd groupings of types eg. (object,string)
   *
   * @param types The JValue from the "type" field of an element
   * @return A validated String which determines what type the element is
   */
  private[schemaddl] def getElemType(types: JValue): Validated[String, String] = {
    val maybeTypes = types match {
      case JString(value) => value.valid
      case JArray(list) => stringifyArray(list)
      case _ => s"Error: Function - 'getElemType' - Type List contains invalid JValue".valid
    }
    maybeTypes match {
      case Validated.Valid(str) =>
        if (str.contains("object")) "object".valid
        else if (str.contains("array")) "array".valid
        else "".valid
      case Validated.Invalid(str) => str.invalid
    }
  }

  /**
   * Check if type property contains more that one type (null isn't counting)
   *
   * @param types string of comma-separated list of JSON Schema types
   * @return true if ``types`` can be product type
   */
  private[schemaddl] def isProductType(types: String): Boolean =
    (types.split(",").toSet - "null").size > 1

  /**
   * Check if "null" is among types in comma-separated list of types
   *
   * @param types string of comma-separated list of JSON Schema types
   * @return true if types contains null
   */
  private[schemaddl] def isNullable(types: String): Boolean =
    types.split(",").toSet.contains("null")

  /**
   * Extract list of types from comma-separated list of JSON Schema types
   *
   * @param types string with comma-separated list of types
   * @return list of all types without null
   */
  private[schemaddl] def extractTypesFromProductType(types: String): List[String] =
    types.split(",").toList.filterNot(_ == "null")

  /**
   * Tries to update properties map with new type with null if it's presented
   *
   * @param properties JSON Schema properties
   * @param currentType type to set
   * @return JSON Schema properties with updated types
   */
  private[schemaddl] def updateType(properties: Map[String, String], currentType: String): Map[String, String] =
    properties.get("type") match {
      case Some(t) if isNullable(t) =>
        properties.updated("type", currentType + ",null")
      case Some(t) =>
        properties.updated("type", currentType)
      case _ =>
        properties
    }

  /**
   * Organising all of the key -> value pairs in the Map by alphabetical order
   *
   * @param paths The Map that needs to be ordered.
   * @return an ordered ListMap of paths that are now in
   *         alphabetical order.
   */
  private[schemaddl] def getOrderedMap(paths: Map[String, Map[String, String]]): ListMap[String, Map[String, String]] =
    ListMap(paths.toSeq.sortBy(_._1):_*)
}
