package com.snowplowanalytics.iglu.ctl

import java.io.File
import java.nio.file.{Files, Paths}

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer}
import com.snowplowanalytics.iglu.ctl.FileUtils.{JsonFile, getJsonFiles}
import com.snowplowanalytics.iglu.ctl.GenerateJavaCommand.{ClassDefinition, JavaFile, JavaOutput}
import com.snowplowanalytics.iglu.ctl.Utils.{modelGroup, splitValidations}
import com.snowplowanalytics.iglu.schemaddl.{FlatSchema, IgluSchema, ModelGroup}

import scalaz.Scalaz._
import scalaz._

/**
  * Created by kirwin on 3/31/17.
  */
case class GenerateJavaCommand(input: File,
                               output: File,
                               packageSuffix: String) extends Command.CtlCommand {

  val effectiveSuffix =
    if (packageSuffix.startsWith(".") || packageSuffix.isEmpty) {
      packageSuffix
    }
    else {
      "." + packageSuffix
    }

  def processJava(): Unit = {
    val allFiles = getJsonFiles(input)
    val (failures, jsons) = splitValidations(allFiles)

    if (failures.nonEmpty) {
      println("JSON Parsing errors:")
      println(failures.mkString("\n"))
    }

    jsons match {
      case Nil       => sys.error(s"Directory [${input.getAbsolutePath}] does not contain any valid JSON files")
      case someJsons =>
        val outputs = transformSelfDescribing(someJsons)
        outputResult(outputs)
    }
  }

  /**
    * Transform list of JSON files to a single [[JavaOutput]] containing
    * all data to produce Java class files
    *
    * @param files list of valid JSON Files, supposed to be Self-describing JSON Schemas
    * @return transformation result containing all data to output
    */
  private[ctl] def transformSelfDescribing(files: List[JsonFile]): JavaOutput = {
    // Parse Self-describing Schemas
    val (schemaErrors, schemas) = splitValidations(files.map(_.extractSelfDescribingSchema))

    // Build class definitions from JSON Schemas
    val validatedClasses = schemas.map(schema => selfDescSchemaToClass(schema).map(c => (schema.self, c)))
    val (javaErrors, javaPairs) = splitValidations(validatedClasses)
    val ddlMap = groupWithLast(javaPairs)

    // Build java files
    val outputFiles = for {
      ddl <- ddlMap.values
    } yield makeJavaFile(ddl)

    JavaOutput(outputFiles.toList, warnings = schemaErrors ++ javaErrors)
  }

  /**
    * Aggregate list of Description-Definition pairs into map, so in value will be left
    * only table definition for latest revision-addition Schema
    * Use this to be sure vendor_tablename_1 always generated for latest Schema
    *
    * @param ddls list of pairs
    * @return Map with latest table definition for each Schema addition
    */
  private def groupWithLast(ddls: List[(SchemaKey, ClassDefinition)]) = {
    val aggregated = ddls.foldLeft(Map.empty[ModelGroup, (SchemaKey, ClassDefinition)]) {
      case (acc, (description, definition)) =>
        acc.get(modelGroup(description)) match {
          case Some((desc, defn)) if desc.version.revision < description.version.revision =>
            acc ++ Map((modelGroup(description), (description, definition)))
          case Some((desc, defn)) if desc.version.revision == description.version.revision &&
            desc.version.addition < description.version.addition =>
            acc ++ Map((modelGroup(description), (description, definition)))
          case None =>
            acc ++ Map((modelGroup(description), (description, definition)))
          case _ => acc
        }
    }
    aggregated.map { case (revision, (desc, defn)) => (desc, defn) }
  }

  private[ctl] def selfDescSchemaToClass(schema: IgluSchema): Validation[String, ClassDefinition] = {
    val ddl = for {
      flatSchema <- FlatSchema.flattenJsonSchema(schema.schema, false)
    } yield produceClass(flatSchema, schema.self)
    ddl match {
      case Failure(fail) => (fail + s" in [${schema.self.toPath}] Schema").failure
      case success => success
    }
  }

  def versionSuffix(version: SchemaVer): String = {
    if (version.model > 1) {
      "V" + version.model
    }
    else {
      ""
    }
  }

  // Want to get a set of schemas, as we'll generate a single class, but a builder per class
  private def produceClass(flatSchema: FlatSchema, schemaKey: SchemaKey) : ClassDefinition = {

    val packageName = schemaKey.vendor + effectiveSuffix
    val className = javafy(schemaKey.name, true) + versionSuffix(schemaKey.version)

    val classBuilder = EventGeneratorBuilder()
    classBuilder.generateClass(packageName, className, flatSchema, schemaKey.toSchemaUri)

    val packageDir = packageName.replace('.', '/')
    ClassDefinition(s"${packageDir}/${className}", classBuilder)
  }

  private def javafy(schemaName : String, initialUpperCase : Boolean = false) : String  = {
    val buffer = new StringBuilder(schemaName.length)
    var upperCase = initialUpperCase

    for (c <- schemaName) {
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

  private def makeJavaFile(javaClass : ClassDefinition) : JavaFile = {
    JavaFile(javaClass.filename, javaClass.codeModel)
  }

  def outputResult(result: JavaOutput): Unit = {
    // Code generation will write a java and resource dir, but these must exist.
    val targetDir = Paths.get(output.getAbsolutePath)
    Files.createDirectories(targetDir.resolve("java"))
    Files.createDirectories(targetDir.resolve("resources"))

    result.classes.map(_.write(output.getAbsolutePath)).foreach(printMessage)

    result.warnings.foreach(printMessage)

    if (result.warnings.exists(_.contains("Error"))) sys.exit(1)
  }

  /**
    * Print value extracted from scalaz Validation or any other message
    */
  private def printMessage(any: Any): Unit = {
    any match {
      case Success(m) => println(m)
      case Failure(m) => println(m)
      case m => println(m)
    }
  }
}

object GenerateJavaCommand {

  case class JavaOutput(classes: List[JavaFile], warnings: List[String] = Nil)

  case class ClassDefinition(filename: String, codeModel: EventGeneratorBuilder)

  case class JavaFile(file: String, code : EventGeneratorBuilder) {

    def write(basePath : String): Validation[String, String] = {
      code.write(file, basePath)
    }
  }


}
