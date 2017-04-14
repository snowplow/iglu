package com.snowplowanalytics.iglu.ctl

import java.io.File

import com.snowplowanalytics.iglu.schemaddl.FlatSchema
import com.sun.codemodel._

import scalaz.Scalaz._
import scalaz._

/**
  * Created by kirwin on 4/3/17.
  */
class EventGeneratorBuilder(val codeModel : JCodeModel) {


  private val PUBLIC_STATIC_FINAL = JMod.PUBLIC | JMod.STATIC | JMod.FINAL

  private val JAVA_TYPES = collection.immutable.HashMap(
    "boolean" -> codeModel.BOOLEAN ,
    "number" -> codeModel.DOUBLE ,
    "integer" -> codeModel.LONG ,
    "string" -> classRef(classOf[String])
  )

  def generateClass(packageName : String, className : String, flatSchema : FlatSchema, schemaUri : String) : Unit = {
    // We generate a subclass of SelfDescribingJson so it can be used equivalently
    val superClass = codeModel.ref("com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson")
    val pojoClass = codeModel._class(packageName + "." + className)._extends(superClass)

    // Embed the schema as a constant
    val schema = pojoClass.field(PUBLIC_STATIC_FINAL, classOf[String] ,"SCHEMA", JExpr.lit(schemaUri))

    val valueMapClass = classRef("java.util.Map", classRef(classOf[String]), classRef(classOf[Object]))

    // Constructor just delegates to super, passing in the schema
    val constructor = pojoClass.constructor(JMod.PRIVATE)
    constructor.param(valueMapClass, "fields")
    constructor.body().invoke("super").arg(JExpr.ref("SCHEMA")).arg(JExpr.ref("fields"))

    // Define a builder class used to construct instances of the schema
    val builderClass = generateBuilderClass(pojoClass, flatSchema)

    // Add factory method for obtaining the builder
    val builderFactory = pojoClass.method(PUBLIC_STATIC_FINAL, builderClass, "builder")
    builderFactory.body()._return(JExpr._new(builderClass))
  }

  private def generateBuilderClass(pojoClass : JDefinedClass, flatSchema : FlatSchema) : JDefinedClass = {
    // Builder will accumulate fields into a wrapped map so it can be easily passed through constructor
    val builderClass = pojoClass._class(PUBLIC_STATIC_FINAL, "Builder")
    val valueMapClass = classRef("java.util.Map", classRef(classOf[String]), classRef(classOf[Object]))
    val mapClass = classRef("java.util.HashMap", classRef(classOf[String]), classRef(classOf[Object]))

    val valuesMap = builderClass.field(JMod.PRIVATE, valueMapClass, "values", JExpr._new(mapClass))

    // Add a setter for each field defined in the schema
    flatSchema.elems.foreach(_ match {
      case (columnName, properties) =>
        generateSetter(builderClass, columnName, properties)
    })

    // Add the final build method to construct and return the instance of the schema
    // We place the checks for required fields here.
    val buildMethod = builderClass.method(JMod.PUBLIC, pojoClass, "build")
    flatSchema.required.foreach(generateFieldCheck(buildMethod, _))
    buildMethod.body()._return(JExpr._new(pojoClass).arg(JExpr.ref("values")))

    builderClass
  }

  private def generateSetter(builderClass : JDefinedClass, fieldName : String, properties : Map[String, String]) : Unit = {
    val setter = builderClass.method(JMod.PUBLIC, builderClass, EventGeneratorBuilder.asJavaName(fieldName))
    val fieldType = properties.get("type").flatMap(JAVA_TYPES.get).getOrElse(classRef(classOf[String]))
    setter.param(fieldType, "value")
    setter.body().invoke(JExpr.ref("values"), "put").arg(fieldName).arg(JExpr.ref("value"))
    setter.body()._return(JExpr._this())
    properties.get("description").map(setter.javadoc().addParam("value").append(_))
  }

  private def generateFieldCheck(buildMethod: JMethod, field: String) = {
    val currentValue = JExpr.ref("values").invoke("get").arg(field).eq(JExpr._null())
    val exception = JExpr._new(classRef(classOf[IllegalArgumentException])).arg(s"${field} is a required field")
    buildMethod.body()._if(currentValue)._then()._throw(exception)
  }

  private def classRef(className : String) : JClass = {
    codeModel.ref(className)
  }

  private def classRef(className : String, typeParams : JClass*) : JClass = {
    codeModel.ref(className).narrow(typeParams:_*)
  }

  private def classRef[T](clazz : Class[T]) : JClass = {
    codeModel.ref(clazz)
  }


  def write(file : String, basePath : String): Validation[String, String] = {
    try {
      codeModel.build(new File(basePath, "java"), new File(basePath, "resources"))
      s"File ${basePath}/java/${file} was written successfully!".success
    }
    catch {
      case e: Exception =>
        val exception = e.toString
        s"File [${basePath}/java/${file}] failed to write: [$exception]".failure
    }
  }

}

object EventGeneratorBuilder {

  def apply() : EventGeneratorBuilder = {
    new EventGeneratorBuilder(new JCodeModel())
  }

  def asJavaName(name : String, initialUpperCase : Boolean = false) : String  = {
    val buffer = new StringBuilder(name.length)
    var upperCase = initialUpperCase

    for (c <- name) {
      c match {
        case '-' | '_'  => upperCase = true
        case x => {
          buffer.append(if (upperCase) x.toUpper else x)
          upperCase = false
        }
      }
    }

    buffer.toString
  }

}