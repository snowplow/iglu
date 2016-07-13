/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
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

// Java
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar

// Iglu Core
import com.snowplowanalytics.iglu.core.SchemaKey

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl._
import com.snowplowanalytics.iglu.schemaddl.FlatSchema
import com.snowplowanalytics.iglu.schemaddl.Migration.buildMigrationMap
import com.snowplowanalytics.iglu.schemaddl.redshift._
import com.snowplowanalytics.iglu.schemaddl.redshift.generators.{
  DdlGenerator,
  JsonPathGenerator,
  DdlFile
}

// Scalaz
import scalaz._
import Scalaz._

// This library
import FileUtils._
import Utils._

case class GenerateCommand(
  input: File,
  output: File,
  db: String = "redshift",        // Isn't checked anywhere
  withJsonPaths: Boolean = false,
  rawMode: Boolean = false,
  schema: Option[String] = None,  // empty for raw, "atomic" for non-raw
  varcharSize: Int = 4096,
  splitProduct: Boolean = false,
  noHeader: Boolean = false,
  force: Boolean = false) extends Command.CtlCommand {

  import GenerateCommand._

  /**
   * Primary working method of `ddl` command
   * Get all JSONs from specified path, try to parse them as JSON Schemas,
   * convert to [[DdlOutput]] (combined table definition, JSON Paths, etc)
   * and output them to specified path, also output errors
   */
  def processDdl(): Unit = {
    val allFiles = getJsonFiles(input)
    val (failures, jsons) = splitValidations(allFiles)

    if (failures.nonEmpty) {
      println("JSON Parsing errors:")
      println(failures.mkString("\n"))
    }

    jsons match {
      case Nil       => sys.error(s"Directory [${input.getAbsolutePath}] does not contain any valid JSON files")
      case someJsons =>
        val outputs =
          if (rawMode) transformRaw(someJsons)
          else transformSelfDescribing(someJsons)
        outputResult(outputs)
    }
  }

  // Self-describing

  /**
   * Transform list of JSON files to a single [[DdlOutput]] containing
   * all data to produce: DDL files, JSONPath files, migrations, etc
   *
   * @param files list of valid JSON Files, supposed to be Self-describing JSON Schemas
   * @return transformation result containing all data to output
   */
  private[ctl] def transformSelfDescribing(files: List[JsonFile]): DdlOutput = {
    val dbSchema = schema.getOrElse("atomic")
    // Parse Self-describing Schemas
    val (schemaErrors, schemas) = splitValidations(files.map(_.extractSelfDescribingSchema))

    // Build table definitions from JSON Schemas
    val validatedDdls = schemas.map(schema => selfDescSchemaToDdl(schema, dbSchema).map(ddl => (schema.self, ddl)))
    val (ddlErrors, ddlPairs) = splitValidations(validatedDdls)
    val ddlMap = groupWithLast(ddlPairs)

    // Build migrations and order-related data
    val migrationMap = buildMigrationMap(schemas)
    val validOrderingMap = Migration.getOrdering(migrationMap)
    val orderingMap = validOrderingMap.collect { case (k, Success(v)) => (k, v) }
    val (_, migrations) = splitValidations(Migrations.reifyMigrationMap(migrationMap, Some(dbSchema), varcharSize))

    // Order table-definitions according with migrations
    val ddlFiles = ddlMap.map { case (description, table) =>
      val order = orderingMap.getOrElse(revisionGroup(description), Nil)
      table.reorderTable(order)
    }.toList
    val ddlWarnings =
      ddlFiles.flatMap(_.ddlFile.warnings)


    // Build DDL-files and JSONPaths file (in correct order and camelCased column names)
    val outputPair = for {
      ddl <- ddlFiles
    } yield (makeDdlFile(ddl), makeJsonPaths(ddl))

    DdlOutput(
      outputPair.map(_._1),
      migrations,
      outputPair.flatMap(_._2),
      warnings = schemaErrors ++ ddlErrors ++ ddlWarnings)
  }

  /**
   * Transform valid Self-describing JSON Schema to DDL table definition
   *
   * @param schema valid JSON Schema including all Self-describing information
   * @param dbSchema DB schema name ("atomic")
   * @return validation of either table definition or error message
   */
  private def selfDescSchemaToDdl(schema: IgluSchema, dbSchema: String): Validation[String, TableDefinition] = {
    val ddl = for {
      flatSchema <- FlatSchema.flattenJsonSchema(schema.schema, splitProduct)
    } yield produceTable(flatSchema, schema.self, dbSchema)
    ddl match {
      case Failure(fail) => (fail + s" in [${schema.self.toPath}] Schema").failure
      case success => success
    }
  }

  // Header Section for a Redshift DDL File
  def redshiftDdlHeader = CommentBlock(Vector(
    s"AUTO-GENERATED BY ${generated.ProjectSettings.name} DO NOT EDIT",
    s"Generator: ${generated.ProjectSettings.name} ${generated.ProjectSettings.version}",
    s"Generated: ${new SimpleDateFormat("YYYY-MM-dd HH:mm").format(Calendar.getInstance.getTime)}"
  ))

  // Header section with additional space
  def header = if (noHeader) Nil else List(redshiftDdlHeader, Empty)

  /**
   * Produce table from flattened Schema and valid JSON Schema description
   *
   * @param flatSchema ordered map of flatten JSON properties
   * @param schemaKey JSON Schema description
   * @param dbSchema DB schema name ("atomic")
   * @return table definition
   */
  private def produceTable(flatSchema: FlatSchema, schemaKey: SchemaKey, dbSchema: String): TableDefinition = {
    val (path, filename) = getFileName(schemaKey)
    val tableName = StringUtils.getTableName(schemaKey)
    val schemaCreate = CreateSchema(dbSchema)
    val table = DdlGenerator.generateTableDdl(flatSchema, tableName, Some(dbSchema), varcharSize, rawMode)
    val commentOn = DdlGenerator.getTableComment(tableName, Some(dbSchema), schemaKey)
    val ddlFile = DdlFile(header ++ List(schemaCreate, Empty, table, Empty, commentOn))
    TableDefinition(path, filename, ddlFile)
  }

  // Raw

  /**
   * Transform list of raw JSON Schemas (without Self-describing info)
   * to raw Ddl output (without migrations, additional data and
   * without explicit order)
   *
   * @param files list of JSON Files (assuming JSON Schemas)
   * @return transformation result containing all data to output
   */
  private[ctl] def transformRaw(files: List[JsonFile]): DdlOutput = {
    val (schemaErrors, ddlFiles) = splitValidations(files.map(jsonToRawTable))
    val ddlWarnings = ddlFiles.flatMap(_.ddlFile.warnings)

    val outputPair = for {
      ddl <- ddlFiles
    } yield (makeDdlFile(ddl), makeJsonPaths(ddl))

    DdlOutput(outputPair.map(_._1), Nil, outputPair.flatMap(_._2), warnings = schemaErrors ++ ddlWarnings)
  }

  /**
   * Generate table definition from raw (non-self-describing JSON Schema)
   *
   * @param json JSON Schema
   * @return validated table definition object
   */
  private def jsonToRawTable(json: JsonFile): Validation[String, TableDefinition] = {
    val ddl = FlatSchema.flattenJsonSchema(json.content, splitProduct).map { flatSchema =>
      produceRawTable(flatSchema, json.fileName)
    }
    ddl match {
      case Failure(fail) => (fail + s" in [${json.fileName}] file").failure
      case success => success
    }
  }

  /**
   * Produces all data required for raw DDL file, including it's path, filename,
   * header and DDL object. Raw file however doesn't contain anything
   * Snowplow- or Iglu-specific, thus JsonFile isn't required to be Self-describing
   * and we cannot produce migrations or correct column order for raw DDL
   *
   * @param flatSchema fields mapped to it's properties
   * @param fileName JSON file, containing filename and content
   * @return DDL File object with all required information to output it
   */
  private def produceRawTable(flatSchema: FlatSchema, fileName: String): TableDefinition = {
    val name = StringUtils.getTableName(fileName)
    val schemaCreate = schema.map(CreateSchema(_)) match {
      case Some(sc) => List(sc, Empty)
      case None => Nil
    }
    val table = DdlGenerator.generateTableDdl(flatSchema, name, schema, varcharSize, rawMode)
    val comment = DdlGenerator.getTableComment(name, schema, fileName)
    val ddlFile = DdlFile(header ++ schemaCreate ++ List(table, Empty, comment))
    TableDefinition(".", name, ddlFile)
  }


  // Common

  /**
   * Make Redshift DDL file out of table definition
   *
   * @param tableDefinition table definition object
   * @return text file with Redshift table DDL
   */
  private def makeDdlFile(tableDefinition: TableDefinition): TextFile = {
    val snakified = tableDefinition.toSnakeCase
    TextFile(new File(new File(snakified.path), snakified.fileName + ".sql"), snakified.ddlFile.render(Nil))
  }

  /**
   * Make JSONPath file out of table definition
   *
   * @param ddl table definition
   * @return text file with JSON Paths if option is set
   */
  private def makeJsonPaths(ddl: TableDefinition): Option[TextFile] = {
    val jsonPath = withJsonPaths.option(JsonPathGenerator.getJsonPathsFile(ddl.getCreateTable.columns, rawMode))
    jsonPath.map { content =>
      TextFile(new File(new File(ddl.path), ddl.fileName + ".json"), content)
    }
  }

  /**
   * Output end result
   */
  def outputResult(result: DdlOutput): Unit = {
    result.ddls
      .map(_.setBasePath("sql"))
      .map(_.setBasePath(output.getAbsolutePath))
      .map(_.write(force)).foreach(printMessage)

    result.jsonPaths
      .map(_.setBasePath("jsonpaths"))
      .map(_.setBasePath(output.getAbsolutePath))
      .map(_.write(force)).foreach(printMessage)

    result.migrations
      .map(_.setBasePath("sql"))
      .map(_.setBasePath(output.getAbsolutePath))
      .map(_.write(force)).foreach(printMessage)

    result.warnings.foreach(printMessage)
  }
}

object GenerateCommand {

  /**
   * Class holding an aggregated output ready to be written
   * with all warnings collected due transofmrations
   *
   * @param ddls list of files with table definitions
   * @param migrations list of files with available migrations
   * @param jsonPaths list of JSONPaths files
   * @param warnings all warnings collected in process of parsing and
   *                 transformation
   */
  case class DdlOutput(
    ddls: List[TextFile],
    migrations: List[TextFile] = Nil,
    jsonPaths: List[TextFile] = Nil,
    warnings: List[String] = Nil)

  /**
   * Class holding all information for file with DDL
   *
   * @param path base directory for file
   * @param fileName DDL file name
   * @param ddlFile list of statements ready to be rendered
   */
  case class TableDefinition(path: String, fileName: String, ddlFile: DdlFile) {
    /**
     * Pick columns listed in `order` (and presented in table definition) and
     * append them to the end of original create table statemtnt, leaving not
     * listed in `order` on their places
     *
     * @todo this logic should be contained in DDL AST as pair of DdlFile 
     *       and JSONPaths file because they're really tightly coupled
     *       Redshift output
     * @param order sublist of column names in right order that should be
     *              appended to the end of table
     * @return DDL file object with reordered columns
     */
    def reorderTable(order: List[String]): TableDefinition = {
      val statements = ddlFile.statements.map {
        case statement: CreateTable => sortColumns(statement, order)
        case statement => statement
      }
      this.copy(ddlFile = ddlFile.copy(statements = statements))
    }

    private[ctl] def getCreateTable: CreateTable =
      ddlFile.statements.collect {
        case statement: CreateTable => statement
      }
      .head

    private[ctl] def toSnakeCase: TableDefinition = {
      val snakifiedColumns = getCreateTable.columns.map { column =>
        val snakified = StringUtils.toSnakeCase(column.columnName)
        column.copy(columnName = snakified)
      }
      val statements = ddlFile.statements.map {
        case statement: CreateTable => statement.copy(columns = snakifiedColumns)
        case statement => statement
      }
      val updatedFile = ddlFile.copy(statements = statements)
      this.copy(ddlFile = updatedFile)
    }

    private def sortColumns(createTable: CreateTable, order: List[String]): CreateTable = {
      val columns = createTable.columns
      val columnMap = columns.map(c => (c.columnName, c)).toMap
      val addedOrderedColumns = order.flatMap(columnMap.get)
      val columnsToSort = order intersect columns.map(_.columnName)
      val initialColumns = columns.filterNot(c => columnsToSort.contains(c.columnName))
      val orderedColumns = initialColumns ++ addedOrderedColumns
      createTable.copy(columns = orderedColumns)
    }
  }

  /**
   * Get the file path and name from self-describing info
   * Like (com.mailchimp, subscribe_1)
   *
   * @param flatSelfElems all information from Self-describing schema
   * @return pair of relative filepath and filename
   */
  def getFileName(flatSelfElems: SchemaKey): (String, String) = {
    // Make the file name
    val version = "_".concat(flatSelfElems.version.asString.replaceAll("-[0-9]+-[0-9]+", ""))
    val file = flatSelfElems.name.replaceAll("([^A-Z_])([A-Z])", "$1_$2").toLowerCase.concat(version)

    // Return the vendor and the file name together
    (flatSelfElems.vendor, file)
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

  /**
   * Aggregate list of Description-Definition pairs into map, so in value will be left
   * only table definition for latest revision-addition Schema
   * Use this to be sure vendor_tablename_1 always generated for latest Schema
   *
   * @param ddls list of pairs
   * @return Map with latest table definition for each Schema addition
   */
  private def groupWithLast(ddls: List[(SchemaKey, TableDefinition)]) = {
    val aggregated = ddls.foldLeft(Map.empty[ModelGroup, (SchemaKey, TableDefinition)]) {
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
}
