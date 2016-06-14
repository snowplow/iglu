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
package generators

// Scalaz
import scalaz._
import Scalaz._

// Scala
import scala.annotation.tailrec

// json4s
import org.json4s._

// This project
import utils.{ MapUtils => MU }
import SchemaData._

/**
 * Flattens a JsonSchema into Strings representing the path to a field.
 * This will be linked with a Map of attributes that the field possesses.
 */
object SchemaFlattener {
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
  private[generators] case class SubSchema(elems: Map[String, Map[String, String]], required: Set[String])

  /**
   * Helper classes to extract info from JSON Schema's JValue
   */
  private[generators] abstract sealed trait Info

  /**
   * Helper class for all primitive types
   */
  private[generators] case object PrimitiveInfo extends Info

  /**
   * Helper class for arrays
   */
  private[generators] case object ArrayInfo extends Info

  /**
   * Helper class for storing information extracted from JSON Schema's object
   *
   * @param properties list of all object properties with it's names
   * @param required set of fields listed in required property
   */
  private[generators] case class ObjectInfo(properties: List[JField], required: Set[String]) extends Info

  /**
   * Helper object extracted from JSON Schema's object which should be
   * represented as VARCHAR(4096) in future
   */
  private[generators] case object FlattenObjectInfo extends Info

  /**
   * Flattens a JsonSchema into a useable Map of Strings and Attributes.
   * - Will extract the self-describing elements of the JsonSchema out
   * - Will then grab the first properties list and begin the recursive function
   * - Will then return the validated object containing paths, attributes, etc
   *
   * @param jSchema the JSON Schema which we will process
   * @param splitProduct whether we need to split product types to different keys
   * @return a validated map of keys and attributes or a failure string
   */
  def flattenJsonSchema(jSchema: JValue, splitProduct: Boolean): Validation[String, FlatSchema] = {
    // Match against the Schema and check that it is properly formed: i.e. wrapped in { ... }
    jSchema match {
      case JObject(list) => {
        // Analyze the base level of the schema
        getElemInfo(list) match {
          case Success(ObjectInfo(properties, required)) => {
            processProperties(properties, requiredKeys = required, requiredAccum = required) match {
              case Success(subSchema) => {
                val elems = if (splitProduct) splitProductTypes(subSchema.elems) else subSchema.elems
                FlatSchema(MU.getOrderedMap(elems), subSchema.required).success
              }
              case Failure(str) => str.failure
            }
          }
          case Success(FlattenObjectInfo) => {
            FlatSchema(MU.getOrderedMap(Map.empty[String, Map[String, String]]), Set.empty[String]).success
          }
          case Failure(str) => str.failure
          case _ => s"Error: Function - 'flattenJsonSchema' - JsonSchema does not begin with an 'object' & 'properties'".failure
        }
      }
      case _ => s"Error: Function - 'flattenJsonSchema' - Invalid Schema passed to flattener".failure
    }
  }

  /**
   * Attempts to extract the self describing elements of the
   * JsonSchema that we are processing.
   *
   * @param jSchema the self describing json schema that needs to be processed
   * @return a validated object containing all needed info
   */
  def getSelfDescElems(jSchema: JValue): Validation[String, SelfDescInfo] = {
    val vendor  = (jSchema \ "self" \ "vendor").extractOpt[String]
    val name    = (jSchema \ "self" \ "name").extractOpt[String]
    val version = (jSchema \ "self" \ "version").extractOpt[String]

    val vendorPattern  = """[a-zA-Z0-9_\.-]+"""
    val namePattern    = """[a-zA-Z0-9_-]+"""
    val versionPattern = """\d+-\d+-\d+"""

    (vendor, name, version) match {
      case (Some(vendor), Some(name), Some(version)) => {
        if (!vendor.matches(vendorPattern)) {
          (s"Error: Function - 'getSelfDescElems' - Vendor [$vendor] doesn't conform pattern. " +
            "Must consist of only letters, numbers, underscores, hyphens and periods.").failure
        } else if (!name.matches(namePattern)) {
          (s"Error: Function - 'getSelfDescElems' - Name [$name] doesn't conform pattern. " +
            "Must consist of only letters, numbers, underscores and hyphens.").failure
        } else if (!version.matches(versionPattern)) {
          (s"Error: Function - 'getSelfDescElems' - Version [$version] doesn't conform pattern. " +
            "Must look like 2-0-1.").failure
        } else {
          SelfDescInfo(vendor, name, version).success
        }
      }
      case (_, _, _) => s"Error: Function - 'getSelfDescElems' - Schema does not contain all needed self describing elements".failure
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
          case Some(types) if isProductType(types) => {
            extractTypesFromProductType(types).map(t => (k + "_" + t -> updateType(v, t))).toMap
          }
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
  private[generators] def processProperties(propertyList: List[JField],
                                            accum: Map[String, Map[String, String]] = Map(),
                                            accumKey: String = "",
                                            requiredKeys: Set[String] = Set.empty,
                                            requiredAccum: Set[String] = Set.empty ):
    Validation[String, SubSchema] = {

    propertyList match {
      case x :: xs => {
        val res: Validation[String, SubSchema] = x match {
          case (key, JObject(list)) => {
            getElemInfo(list) match {
              case Success(ObjectInfo(properties, required)) => {
                val currentLevelRequired = if (requiredAccum.contains(key)) { required } else { Set.empty[String] }
                val keys = properties.map(_._1).filter(currentLevelRequired.contains).map(accumKey + key + "." + _)
                processProperties(properties, Map(), accumKey + key + ".", keys.toSet, required)
              }
              case Success(FlattenObjectInfo) => {
                SubSchema(Map(accumKey + key -> Map("type" -> "string")), Set.empty[String]).success
              }
              case Success(ArrayInfo) => {
                SubSchema(Map(accumKey + key -> Map("type" -> "array")), Set.empty[String]).success
              }
              case Success(_) => processAttributes(list) match {
                case Success(attr) => SubSchema(Map(accumKey + key -> attr), Set.empty[String]).success
                case Failure(str)  => str.failure
              }
              case Failure(str) => str.failure
            }
          }
          case _ => s"Error: Function - 'processProperties' - Invalid List Tuple2 Encountered".failure
        }

        res match {
          case Success(goodRes) => {
            processProperties(xs, (accum ++ goodRes.elems), accumKey, requiredKeys ++ goodRes.required, requiredAccum)
          }
          case Failure(badRes) => badRes.failure
        }
      }
      case Nil => SubSchema(accum, requiredKeys).success
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
  private[generators] def processAttributes(attributes: List[JField], accum: Map[String, String] = Map()): Validation[String, Map[String, String]] =
    attributes match {
      case x :: xs => {
        x match {
          case (key, JArray(value))  => {
            stringifyArray(value) match {
              case Success(strs) => processAttributes(xs, (accum ++ Map(key -> strs)))
              case Failure(str) => str.failure
            }
          }
          case (key, JBool(value))    => processAttributes(xs, (accum ++ Map(key -> value.toString)))
          case (key, JInt(value))     => processAttributes(xs, (accum ++ Map(key -> value.toString)))
          case (key, JDecimal(value)) => processAttributes(xs, (accum ++ Map(key -> value.toString)))
          case (key, JDouble(value))  => processAttributes(xs, (accum ++ Map(key -> value.toString)))
          case (key, JNull)           => processAttributes(xs, (accum ++ Map(key -> "null")))
          case (key, JString(value))  => processAttributes(xs, (accum ++ Map(key -> value)))
          case _                      => s"Error: Function - 'processAttributes' - Invalid JValue found".failure
        }
      }
      case Nil => accum.success
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
  private[generators] def stringifyArray(list: List[JValue], accum: String = "", delim: String = ","): Validation[String, String] =
    list match {
      case x :: xs => {
        x match {
          case JString(str) => stringifyArray(xs, (accum + delim + str))
          case JInt(x)      => stringifyArray(xs, (accum + delim + x.toString))
          case JDecimal(x)  => stringifyArray(xs, (accum + delim + x.toString))
          case JDouble(x)   => stringifyArray(xs, (accum + delim + x.toString))
          case JNull        => stringifyArray(xs, (accum + delim + "null"))
          case _            => s"Error: Function - 'processList' - Invalid JValue: $x in list".failure
        }
      }
      case Nil => accum.drop(1).success
    }


  /**
   * Get all required keys taken from JSON Schema object's "required" key
   *
   * @param jObject JSON object containing JSON Schema
   * @return validated list of required fields
   */
  private[generators] def getRequiredProperties(jObject: Map[String, JValue]): Validation[String, List[String]] = {
    // Helper function, validates each element as string
    def JStringToString(array: List[JValue]): Validation[String, List[String]] =
      array.foldLeft(Nil.success: Validation[String, List[String]]) { (acc, str) =>
        acc match {
          case Success(l) => str match {
            case JString(s) => (s :: l).success
            case _ => "required property must contain only strings".failure
          }
          case Failure(f) => f.failure
        }
      }
    jObject.get("required") match {
      case Some(required) => required match {
        case JArray(list) => JStringToString(list)
        case _ => "required property must contain array of keys".failure
      }
      case None => Nil.success
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
  private[generators] def getElemInfo(maybeAttrList: List[JField]): Validation[String, Info] = {
    val objectMap = maybeAttrList.toMap
    objectMap.get("type") match {
      case Some(types: JValue) => {
        getElemType(types) match {
          case (Success(elemType)) => {
            elemType match {
              case "object" => {
                // TODO: probably won't work on complex product types
                objectMap.get("properties") match {
                  case Some(JObject(props)) =>
                    val requiredFields = getRequiredProperties(objectMap)
                    requiredFields match {
                      case Success(required) => ObjectInfo(props, required.toSet).success
                      case Failure(str)      => str.failure
                    }
                  case _ => FlattenObjectInfo.success
                }
              }
              case "array"  => ArrayInfo.success
              case _        => PrimitiveInfo.success // Pass back a successful empty Map for a normal entry (Should come up with something better...)
            }
          }
          case Failure(str) => str.failure
        }
      }
      case None if objectMap.get("enum").isDefined => PrimitiveInfo.success
      case _ => FlattenObjectInfo.success
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
  private[generators] def getElemType(types: JValue): Validation[String, String] = {
    val maybeTypes = types match {
      case JString(value) => value.success
      case JArray(list) => stringifyArray(list)
      case _ => s"Error: Function - 'getElemType' - Type List contains invalid JValue".failure
    }
    maybeTypes match {
      case Success(str) => {
        if (str.contains("object")) {
          "object".success
        }
        else if (str.contains("array")) {
          "array".success
        }
        else {
          "".success
        }
      }
      case Failure(str) => str.failure
    }
  }

  /**
   * Check if type property contains more that one type (null isn't counting)
   *
   * @param types string of comma-separated list of JSON Schema types
   * @return true if ``types`` can be product type
   */
  private[generators] def isProductType(types: String): Boolean =
    (types.split(",").toSet - "null").size > 1

  /**
   * Check if "null" is among types in comma-separated list of types
   *
   * @param types string of comma-separated list of JSON Schema types
   * @return true if types contains null
   */
  private[generators] def isNullable(types: String): Boolean =
    types.split(",").toSet.contains("null")

  /**
   * Extract list of types from comma-separated list of JSON Schema types
   *
   * @param types string with comma-separated list of types
   * @return list of all types without null
   */
  private[generators] def extractTypesFromProductType(types: String): List[String] =
    types.split(",").toList.filterNot(_ == "null")

  /**
   * Tries to update properties map with new type with null if it's presented
   *
   * @param properties JSON Schema properties
   * @param currentType type to set
   * @return JSON Schema properties with updated types
   */
  private[generators] def updateType(properties: Map[String, String], currentType: String): Map[String, String] =
    properties.get("type") match {
      case Some(t) if isNullable(t) =>
        properties.updated("type", currentType + ",null")
      case Some(t) =>
        properties.updated("type", currentType)
      case _ =>
        properties
    }
}
