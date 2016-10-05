/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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

// Scalaz
import scalaz._
import Scalaz._

// Json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// Jackson
import com.fasterxml.jackson.core.JsonParseException

// Scala
import scala.io.Source

// Java
import java.io.{ IOException, PrintWriter, File }

// Iglu Core
import com.snowplowanalytics.iglu.core.Containers._

object FileUtils {

  type ValidJsonFileList = List[Validation[String, JsonFile]]

  type ValidJsonFileStream = Stream[Validation[String, JsonFile]]

  // AttachToSchema can be used both to extract and attach
  implicit val schemaExtract = com.snowplowanalytics.iglu.core.json4s.AttachToSchema

  val separator = System.getProperty("file.separator", "/")

  /**
   * Class representing file path and content ready
   * to be written to file system
   *
   * @param file java file object for future write
   * @param content text (DDL) to write
   */
  case class TextFile(file: File, content: String) {
    /**
     * Prepend directory path to Migration file
     *
     * @param dir single directory or composed path (OS-compat on user behalf)
     * @return modified (not-mutated) with new base path
     */
    def setBasePath(dir: String): TextFile =
      this.copy(file = new File(new File(dir), file.getPath))

    /**
     * Try to write [[content]] to [[file]] on file system
     *
     * @return validation with success or error message
     */
    def write(force: Boolean = false): Validation[String, String] = {
      writeToFile(file.getName, file.getParentFile.getAbsolutePath, content, force)
    }
  }

  /**
   * Companion object for [[TextFile]] with additional constructors
   */
  object TextFile {
    def apply(file: String, content: String): TextFile =
      TextFile(new File(file), content)
  }

  /**
   * Class representing JSON content and reference on filesystem
   *
   * @param content valid JSON content
   * @param origin real file object
   */
  class JsonFile private(val content: JValue, val origin: File) {
    /**
     * Try to extract Self-describing JSON Schema from JSON file
     * [[JsonFile]] not neccessary contains JSON Schema, it also used for storing
     * plain JSON, so this method isn't always successful
     *
     * @return validation JSON Schema
     */
    def extractSelfDescribingSchema: Validation[String, SelfDescribingSchema[JValue]] = {
      val optionalSchema = content.toSchema.map(_.success[String])
      optionalSchema.getOrElse(s"Cannot extract Self-describing JSON Schema from JSON file [$getKnownPath]".failure)
    }

    def fileName: String = origin.getName

    def path: String = origin.getCanonicalPath

    /**
     * Return known part of file path. If `path` is present it will join it
     * with filename, otherwise return just `fileName`
     *
     * @return fileName or full absolute path
     */
    def getKnownPath: String = origin.getAbsolutePath
  }

  object JsonFile {
    def apply(content: JValue, origin: File) =
      new JsonFile(content, origin)
  }

  /**
   * Creates a new file with the contents of the list inside.
   *
   * @param fileName The name of the new file
   * @param fileDir The directory we want the file to live in, w/o trailing slash
   * @param content Content of file
   * @return a success or failure string about the process
   */
  def writeToFile(fileName: String, fileDir: String, content: String, force: Boolean = false): Validation[String, String] = {
    val path = fileDir + "/" + fileName
    try {
      makeDir(fileDir) match {
        case true =>
          // Attempt to open the file...
          val file = new File(path)
          val contentChanged = isNewContent(file, content)
          if (!file.exists()) {
            printToFile(file)(_.println(content))
            s"File [${file.getAbsolutePath}] was written successfully!".success
          } else if (contentChanged && !force) {
            s"File [${file.getAbsolutePath}] already exists and probably was modified manually. You can use --force to override".failure
          } else if (force) {
            printToFile(file)(_.println(content))
            s"File [${file.getAbsolutePath}] was overridden successfully (no change)!".success
          } else {
            s"File [${file.getAbsolutePath}] was not modified".success
          }
        case false => s"Could not make new directory to store files in [$fileDir] - Check write permissions".failure
      }
    } catch {
      case e: Exception =>
        val exception = e.toString
        s"File [$path] failed to write: [$exception]".failure
    }
  }

  /**
   * Prints a single line to a file
   *
   * @param f The File we are going to print to
   */
  private def printToFile(f: File)(op: PrintWriter => Unit) {
    val p = new PrintWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }

  /**
   * Creates a new directory at the path specified and returns a boolean
   * on if it was successful
   *
   * @param dir path that needs to be created
   * @return true if it was created or already existed
   */
  private def makeDir(dir: String): Boolean = {
    val file = new File(dir)
    if (!file.exists()) file.mkdirs else true
  }

  /**
   * Get list of JSON Files from either a directory (multiple JSON Files)
   * or single file (one-element list)
   * This is eager implementation, which means it will load all files in
   * memory once it is invoked
   *
   * @param file Java File object pointing to File or dir
   * @param predicate optional predicate allowing to filter files by their
   *                  path or access rights, so they not appear as failures
   * @return list of validated JsonFiles
   */
  def getJsonFiles(file: File, predicate: Option[File => Boolean] = None): ValidJsonFileList =
    getJsonFilesStream(file, predicate).toList

  /**
   * Lazily get list of JSON Files from either a directory (multiple JSON Files)
   * or single file (one-element list)
   *
   * @param file Java File object pointing to File or dir
   * @param predicate optional predicate allowing to filter files by their
   *                  path or access rights, so they not appear as failures
   * @return list of validated JsonFiles
   */
  def getJsonFilesStream(file: File, predicate: Option[File => Boolean] = None): ValidJsonFileStream =
    if (!file.exists()) Failure(s"Path [${file.getAbsolutePath}] doesn't exist") #:: Stream.empty
    else if (file.isDirectory) predicate match {
      case Some(p) => streamAllFiles(file).filter(p).map(getJsonFile)
      case None    => streamAllFiles(file).map(getJsonFile)
    }
    else getJsonFile(file) #:: Stream.empty

  /**
   * Recursively and lazily get all files in ``dir`` except hidden
   *
   * @param dir directory to scan
   * @return list of found files
   */
  def streamAllFiles(dir: File): Stream[File] = {
    def scanSubdir(subDir: File): Array[File] = {
      val these = subDir.listFiles.filterNot(_.getName.startsWith("."))
      these ++ these.filter(_.isDirectory).flatMap(scanSubdir)
    }
    scanSubdir(dir).filter(_.isFile).toStream
  }

  /**
   * Get Json File from a single [[File]] object
   *
   * @param file Java File object
   * @return validated Json File or failure message
   */
  def getJsonFile(file: File): Validation[String, JsonFile] =
    getJsonFromFile(file) match {
      case Success(json) => JsonFile(json, file).success
      case Failure(str) => str.failure
    }

  /**
   * Returns a validated JSON from the specified path
   *
   * @param file file object with JSON
   * @return a validation either be correct JValue or error as String
   */
  def getJsonFromFile(file: File): Validation[String, JValue] = {
    try {
      val content = Source.fromFile(file).mkString
      parse(content).success
    } catch {
      case e: JsonParseException =>
        val exception = e.getMessage
        s"File [${file.getAbsolutePath}] contents failed to parse into JSON: [$exception]".failure
      case e: Exception =>
        val exception = e.getMessage
        s"File [${file.getAbsolutePath}] fetching and parsing failed: [$exception]".failure
    }
  }

  /**
   * Check if file has changed content
   * All lines changed starting with -- (SQL comment) or blank lines
   * are ignored
   *
   * @param file existing file to check
   * @param content new content
   * @return true if file has different content or unavailable
   */
  def isNewContent(file: File, content: String): Boolean = {
    try {
      val oldContent = Source.fromFile(file)
        .getLines()
        .map(_.trim)
        .filterNot(_.isEmpty)
        .filterNot(_.startsWith("--"))
        .toList

      val newContent = content
        .split("\n")
        .map(_.trim)
        .filterNot(_.isEmpty)
        .filterNot(_.startsWith("--"))
        .toList

      oldContent != newContent

    } catch {
      case e: IOException => true
    }
  }

  /**
   * OS-specific filesystem path-split
   *
   * @param path absolute path to file
   * @return list of all parts of absolute file path ready to be joined by `separator`
   */
  def splitPath(path: String): List[String] = {
    val sep = if (separator == """\""") """\\""" else separator
    path.split(sep).toList
  }

  def splitPath(file: File): List[String] = splitPath(file.getAbsolutePath)

  /**
   * Predicate used to filter only files which Iglu path contains `jsonschema`
   * as format
   *
   * @param file any real file
   * @return true if third entity of Iglu path is `jsonschema`
   */
  def filterJsonSchemas(file: File): Boolean =
    splitPath(file).takeRight(4) match {
      case List(_, _, format, _) => format == "jsonschema"
      case _ => false
    }
}
