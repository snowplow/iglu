/*
 * Copyright (c) 2012-2017 Snowplow Analytics Ltd. All rights reserved.
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

// cats
import cats.data.{EitherT, Ior, IorNel, NonEmptyList}
import cats.{Eq, Show}
import cats.implicits._
import cats.effect.IO

// Json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.parse

// Scala
import scala.io.Source
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

import fs2._

// Java
import java.io.{ IOException, PrintWriter }
import java.nio.file.{ Path, Paths, Files }

// Iglu Core
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.SelfDescribingSchema
import com.snowplowanalytics.iglu.core.json4s.implicits._

import com.snowplowanalytics.iglu.ctl.Common._

sealed trait File[A] extends Serializable { self =>
  def path: Path
  def content: A

  /**
    * Prepend directory path to a file
    * @param dir single directory or composed path (OS-compat on user's behalf), e.g. `root`
    * @return `File` with same content, new path, e.g. `root/old/path.txt`
    */
  def setBasePath(dir: String): File[A] =
    new File[A] {
      def path: Path = Paths.get(dir, self.path.toString)
      def content: A = self.content
    }

  /** Transform text file into a JSON Schema file and validate its content and path */
  def asJson(implicit ev: A =:= String): Either[Error, JsonFile] =
    Either.catchNonFatal(parse(content: String))
      .leftMap(error => Error.ParseError(path, error.getMessage))
      .map(json => self.withContent(json))

  /** Transform JSON file into a JSON Schema file and validate its content and path */
  def asSchema(implicit ev: A =:= JValue): Either[Error, SchemaFile] = {
    val jsonContent: JValue = content
    SelfDescribingSchema.parse(jsonContent).leftMap {
      case ParseError.InvalidSchema => Error.ParseError(path, s"Cannot extract Self-describing JSON Schema from JSON file")
      case other =>  Error.ParseError(path, s"JSON Schema in file [${path.toAbsolutePath}] is not valid, ${other.code}")
    }.ensureOr(schema => Error.PathMismatch(path, schema.self)) {
      schema => Utils.equalPath(self.withContent(jsonContent), schema.self)
    }.map(schema => self.withContent(schema))
  }

  /** Transform text file straight into JSON Schema */
  def asJsonSchema(implicit ev: A =:= String): Either[Error, SchemaFile] =
    asJson.flatMap(_.asSchema)

  /**
    * Try to write [[content]] to [[path]] on file system
    * @return validation with success or error message
    */
  def write(force: Boolean)(implicit A: Show[A]): IO[Either[Error, String]] =
    for {
      absolutePath <- IO(path.toAbsolutePath)
      result <- File.writeToFile(absolutePath, content, force)
    } yield result

  private def withContent[B](newContent: B): File[B] = new File[B] {
    val content = newContent
    val path = self.path
  }

  override def toString: String =
    s"File($path, ${content.toString.slice(0, 128)})"

  override def equals(obj: Any): Boolean =
    obj match {
      case f: File[_] => f.path == self.path && f.content == self.content
      case _ => false
    }
}

/** Module for manipulating file system and constructing instances of [[File]] */
object File {

  val separator: String = System.getProperty("file.separator", "/")

  implicit def eqFile[A: Eq]: Eq[File[A]] =
    Eq.instance((a: File[A], b: File[A]) => a.path == b.path && a.content === b.content)

  /** Constructor for [[TextFile]] */
  def textFile(p: Path, text: String): TextFile = new File[String] {
    def path: Path = p
    def content: String = text
  }

  /** Constructor for [[JsonFile]] */
  def jsonFile(p: Path, json: JValue): JsonFile = new File[JValue] {
    def path: Path = p
    def content: JValue = json
  }

  /**
    * Lazily get list of JSON Files from either a directory (multiple JSON Files)
    * or single file (one-element list)
    * @param input path pointing to a file or directory
    * @param filter optional predicate allowing to filter files by their
    *               path or access rights, so they not appear as failures
    * @return list of validated JsonFiles
    */
  def streamFiles(input: Path, filter: Option[Path => Boolean]): ReadStream[TextFile] =
    for {
      pathObject <- Stream.eval(PathObject.decide(input))
      file <- pathObject match {
        case Right(PathObject.Other(path)) => Stream.emit(Error.ReadError(path, s"Path is neither file or directory").asLeft)
        case Right(PathObject.Dir(path)) => streamPaths(path).filter(unwrapPredicate(filter))
        case Right(PathObject.File(path)) => Stream.emit(path.asRight)
        case Left(error) => Stream.emit(error.asLeft)
      }
      textFile <- file match {
        case Left(error) => Stream.emit(error.asLeft[TextFile])
        case Right(filePath) => Stream.eval(readFile(filePath))
      }
    } yield textFile

  def parseStream[A, B](f: A => Either[Error, B])(origin: ReadStream[A]): ReadStream[B] =
    origin.map(_.flatMap(f))

  /**
    * Read all JSON schemas into memory, preserving any inconsistencies or invalid files found along the way
    * All schemas loaded into memory in order to check their relationships and possible gaps
    * Any gaps or invalid files are non-critical errors and preserved in `Ior.Both`
    * Empty input directory or absence of valid schemas is critical error (`Ior.Left`)
    */
  def readSchemas(input: Path): IO[IorNel[Error, NonEmptyList[SchemaFile]]] =
    for {
      jsonSchemas <- streamFiles(input, Some(filterJsonSchemas)).through(parseStream(_.asJsonSchema)).compile.toList
      schemas = jsonSchemas match {
        case Nil => NonEmptyList.of(Error.ReadError(input, "is empty")).leftIor
        case _ =>
          val checked = jsonSchemas.collect { case Right(schema) => schema } match {
            case Nil =>
              NonEmptyList.of(Error.ReadError(input, "no valid JSON Schemas")).leftIor
            case h :: t =>
              val files = NonEmptyList(h, t)
              val gapErrors = Common.checkSchemasConsistency(files.map(_.content.self)).leftMap(_.map(error => Error.ConsistencyError(error)))
              Ior.fromEither(gapErrors).putRight(files)
          }
          checked.leftMap(_.concat(jsonSchemas.collect { case Left(e) => e }))
      }
    } yield schemas

  /** Recursively and lazily get all non-hidden regular files in `dir` */
  def streamPaths(dir: Path): ReadStream[Path] = {
    def scanSubDir(subDir: Path): ReadStream[Path] =
      listDirectory(subDir).flatMap {
        case Right(PathObject.File(path)) => Stream.emit(path.asRight[Error])
        case Right(PathObject.Dir(path)) =>
          Stream.eval(scanSubDir(path).compile.toVector.map(_.sortBy {
            case Right(file) => file.toString
            case Left(_) => ""
          })).flatMap(list => Stream.emits(list))
        case Right(PathObject.Other(_)) => Stream.empty
        case Left(error) => Stream.emit(error.asLeft[Path])
      }

    scanSubDir(dir)
  }

  /** Read a single text `TextFile` into memory */
  def readFile(file: Path): IO[Either[Error, TextFile]] =
    IO(Source.fromFile(file.toFile).mkString)
      .attempt
      .map {
        case Right(text) => textFile(file, text).asRight[Error]
        case Left(error) => Error.ReadError(file, s"Cannot read: ${error.getMessage}").asLeft[TextFile]
      }

  /** Check that igluctl can write to `path` */
  def checkOutput(path: Path): EitherT[IO, NonEmptyList[Error], Unit] =
    EitherT(IO { if (Files.isWritable(path)) ().asRight else Error.WriteError(path, "is not writable").asLeft.toEitherNel })

  /**
   * Creates a new file with the contents of the list inside.
   *
   * @param path absolute path to the file
   * @param content Content of file
   * @return a success or failure string about the process
   */
  private def writeToFile[A: Show](path: Path, content: A, force: Boolean): IO[Either[Error, String]] = {
    val action = for {
      _ <- makeDir(path.getParent)
      result <- IO {
        val newContent = isNewContent(path, content.show)
        if (!Files.exists(path)) {
          printToFile(path)(_.println(content))
          s"File [$path] was written successfully!".asRight
        } else if (newContent && !force) {
          Error.WriteError(path, "Already exists and probably was modified manually. You can use --force to override").asLeft
        }  else if (newContent && force) {
          printToFile(path)(_.println(content))
          s"File [$path] was overridden successfully!".asRight
        } else if (force) {
          printToFile(path)(_.println(content))
          s"File [$path] was overridden successfully (no change)!".asRight
        } else {
          s"File [$path] was not modified".asRight
        }
      }
    } yield result
    action.handleErrorWith {
      case NonFatal(e) => IO.pure(Error.WriteError(path, e.getMessage).asLeft)
    }
  }

  private sealed trait PathObject extends Product with Serializable

  private object PathObject {
    case class File(path: Path) extends PathObject
    case class Dir(path: Path) extends PathObject
    case class Other(path: Path) extends PathObject

    def decide(path: Path): IO[Either[Error, PathObject]] =
      IO {
        if (Files.isDirectory(path) && Files.isReadable(path))
          Dir(path).asRight
        else if (Files.isRegularFile(path) && Files.isReadable(path))
          File(path).asRight
        else if ((Files.isDirectory(path) || Files.isRegularFile(path)) && !Files.isReadable(path))
          Error.ReadError(path, "Not readable").asLeft
        else
          Other(path).asRight
      } handleErrorWith {
      case e: SecurityException => IO(Error.ReadError(path, e.getMessage).asLeft)
    }
  }

  private def listDirectory(dir: Path): ReadStream[PathObject] = {
    val action = IO { if (Files.exists(dir))
      Files.list(dir).iterator().asScala.map(_.asRight)
    else
      Iterator(Error.ReadError(dir, s"Directory does not exist").asLeft)
    } handleErrorWith {
      case NonFatal(e) => IO(Iterator(Error.ReadError(dir, s"Cannot list directory: ${e.getMessage}").asLeft))
    }
    Stream
      .eval(action)
      .flatMap(iterator => Stream.fromIterator[IO, Either[Error, Path]](iterator))
      .flatMap {
        case Right(path) =>
          Stream
            .eval(IO(Files.isHidden(path)))
            .flatMap(hidden => if (!hidden) Stream.emit(path) else Stream.empty)
            .evalMap(PathObject.decide)
        case Left(error) =>
          Stream.emit(Left(error))
      }
  }


  /**
   * Prints a single line to a file
   *
   * @param f The File we are going to print to
   */
  private def printToFile(f: Path)(op: PrintWriter => Unit): Unit = {
    val p = new PrintWriter(f.toFile)
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
  private def makeDir(path: Path): IO[Either[Error, Unit]] = {
    val action = for {
      exists <- IO(Files.exists(path))
      _ <- if (!exists) makeDir(path.getParent) *> IO(Files.createDirectory(path)) else IO.pure(().asRight)
    } yield ().asRight
    action.handleErrorWith {
      case NonFatal(e) => IO.pure(Error.WriteError(path, e.getMessage).asLeft)
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
  private def isNewContent(file: Path, content: String): Boolean = {
    try {
      val oldContent = Source.fromFile(file.toFile)
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
      case _: IOException => true
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

  def splitPath(file: Path): List[String] =
    splitPath(file.toAbsolutePath.toString)

  /**
   * Predicate used to filter only files which Iglu path contains `jsonschema`
   * as format
   *
   * @param file any real file
   * @return true if third entity of Iglu path is `jsonschema`
   */
  def filterJsonSchemas(file: Path): Boolean =
    splitPath(file).takeRight(4) match {
      case List(_, _, format, _) => format == "jsonschema"
      case _ => false
    }

  private def unwrapPredicate[A](predicate: Option[Path => Boolean]): Either[A, Path] => Boolean =
    predicate match {
      case Some(pp) => {
        case Right(someP) => pp(someP)
        case Left(_) => true
      }
      case None => _ => true
    }}
