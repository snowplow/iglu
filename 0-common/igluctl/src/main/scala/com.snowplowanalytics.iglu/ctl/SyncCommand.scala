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

// scalaz
import scalaz._
import Scalaz._

// scalaj-http
import scalaj.http.{ Http, HttpRequest, HttpResponse }

// json4s
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

// Java
import java.io.File
import java.net.URL
import java.util.UUID

// Iglu core
import com.snowplowanalytics.iglu.core.json4s.StringifySchema

// Schema DDL
import com.snowplowanalytics.iglu.schemaddl.IgluSchema

// This project
import FileUtils._
import SyncCommand._

/**
 * Class holding arguments passed from shell into `sync` igluctl command
 * and command's main logic
 *
 * @param registryRoot full URL (host, port) of Iglu Registry
 * @param masterApiKey mater key UUID which can be used to create any Schema
 * @param inputDir directory with JSON Schemas or single JSON file
 */
case class SyncCommand(registryRoot: HttpUrl, masterApiKey: UUID, inputDir: File) extends Command.CtlCommand {

  private implicit val stringifySchema = StringifySchema

  /**
   * Primary function, performing IO reading, processing and printing results
   */
  def process(): Unit = {
    val apiKeys = getApiKeys(buildCreateKeysRequest)
    val jsons = getJsonFilesStream(inputDir, Some(filterJsonSchemas)).map(_.disjunction)

    val resultsT = for {    // disjunctions nested into list
      request  <- fromXors(buildRequests(apiKeys, jsons))
      response <- fromXor(postSchema(request))
    } yield response
    val results = resultsT.run.map(flattenResult)

    // Sink results-stream into end-of-the-app
    val total = results.foldLeft(Total.empty)((total, report) => total.add(report))
    total.clean { () => apiKeys match {
      case \/-(keys) =>
        deleteKey(keys.read, "Read")
        deleteKey(keys.write, "Write")
      case _ => println("INFO: No keys were created")
    }}
    total.exit()
  }

  /**
   * Build HTTP POST-request with master apikey to create temporary
   * read/write apikeys
   *
   * @return HTTP POST-request ready to be sent
   */
  def buildCreateKeysRequest: HttpRequest @@ CreateKeys = {
    val request = Http(s"$registryRoot/api/auth/keygen")
      .header("apikey", masterApiKey.toString)
      .postForm(List(("vendor_prefix", "*")))
    Tag.of[CreateKeys](request)
  }

  /**
   * Build stream of HTTP requests with auth and Schema as POST data
   * In case of failed `apiKey` - this is one-element (error) Stream
   *
   * @param apiKeys pair of read/write apikeys, possibly containing error
   * @param jsons stream of Json files, each of which can contain some error
   *              (parsing, non-self-describing, etc)
   * @return lazy stream of requests ready to be sent
   */
  def buildRequests(apiKeys: String \/ ApiKeys, jsons: JsonStream): RequestStream = {
    val resultsT = for {
      writeKey <- fromXor(apiKeys.map(_.write))
      json     <- fromXors(jsons)
      schema   <- fromXor(LintCommand.extractSchema(json))
    } yield buildRequest(schema, writeKey)
    resultsT.run
  }

  /**
   * Build HTTP POST-request with JSON Schema and authenticated with temporary
   * write key
   *
   * @param schema valid self-describing JSON Schema
   * @param writeKey temporary apikey allowed to write any Schema
   * @return HTTP POST-request ready to be sent
   */
  def buildRequest(schema: IgluSchema, writeKey: String): HttpRequest @@ PostSchema = {
    val request = Http(s"$registryRoot/api/schemas/${schema.self.toPath}")
      .header("apikey", writeKey)
      .param("isPublic", "false")
      .put(schema.asString)
    Tag.of[PostSchema](request)
  }

  /**
   * Send DELETE request for temporary key.
   * Performs IO
   *
   * @param key UUID of temporary key
   * @param purpose what exact key being deleted, used to log, can be empty
   */
  def deleteKey(key: String, purpose: String): Unit = {
    val request = Http(s"$registryRoot/api/auth/keygen")
      .header("apikey", masterApiKey.toString)
      .param("key", key)
      .method("DELETE")

    Validation.fromTryCatch(request.asString) match {
      case Success(response) if response.isSuccess => println(s"$purpose key $key deleted")
      case Success(response) => println(s"FAILURE: DELETE $purpose $key response: ${response.body}")
      case Failure(throwable) => println(s"FAILURE: $purpose $key: ${throwable.toString}")
    }
  }

  /**
   * End-of-the-world data containing all results of uploading and
   * app closing logic
   */
  case class Total(updates: Int, creates: Int, failures: Int, unknown: Int) {
    /**
     * Print summary information and exit with 0 or 1 status depending on
     * presence of errors during processing
     */
    def exit(): Unit = {
      println(s"TOTAL: ${creates + updates} Schemas successfully uploaded ($creates created; $updates updated)")
      println(s"TOTAL: $failures failed Schema uploads")
      if (unknown > 0) println(s"WARNING: $unknown unknown statuses")

      if (unknown > 0 || failures > 0) sys.exit(1)
      else sys.exit(0)
    }

    /**
     * Modify end-of-the-world object, by sinking reports and printing info
     * Performs IO
     *
     * @param result result of upload
     * @return new modified total object
     */
    def add(result: Result): Total = result match {
      case s @ Result(_, Updated) =>
        println(s"SUCCESS: ${s.asString}")
        copy(updates = updates + 1)
      case s @ Result(_, Created) =>
        println(s"SUCCESS: ${s.asString}")
        copy(creates = creates + 1)
      case s @ Result(_, Failed) =>
        println(s"FAILURE: ${s.asString}")
        copy(failures = failures + 1)
      case s @ Result(_, Unknown) =>
        println(s"FAILURE: ${s.asString}")
        copy(unknown = unknown + 1)
    }

    /**
     * Perform cleaning
 *
     * @param f cleaning function
     */
    def clean(f: () => Unit): Unit = f()
  }
  
  object Total {
    val empty = Total(0,0,0,0)
  }
}

/**
 * Companion objects, containing functions not closed on `masterApiKey`, `registryRoot`, etc
 */
object SyncCommand {

  /**
   * Anything that can bear error message
   */
  type Failing[A] = String \/ A

  /**
   * Lazy stream of JSON files, containing possible error, file info and valid JSON
   */
  type JsonStream = Stream[Failing[JsonFile]]

  /**
   * Lazy stream of HTTP requests ready to be sent, which also can be errors
   */
  type RequestStream = Stream[Failing[HttpRequest @@ PostSchema]]

  // json4s serialization
  private implicit val formats = DefaultFormats

  // OS-specific file separator
  private val separator = System.getProperty("file.separator", "/")

  /**
   * Class container holding temporary read/write apikeys, extracted from
   * server response using `getApiKey`
   *
   * @param read stringified UUID for read apikey (not used anywhere)
   * @param write stringified UUID for write apikey (not used anywhere)
   */
  case class ApiKeys(read: String, write: String)

  /**
   * Common server message extracted from HTTP JSON response
   *
   * @param status HTTP status code
   * @param message human-readable message
   * @param location optional URI available for successful upload
   */
  case class ServerMessage(status: Int, message: String, location: Option[String]) {
    def asString: String =
      s"$message ${location.map("at " + _ + " ").getOrElse("")} ($status)"
  }

  /**
   * ADT representing all possible statuses for Schema upload
   */
  sealed trait Status extends Serializable
  case object Updated extends Status
  case object Created extends Status
  case object Unknown extends Status
  case object Failed extends Status

  /**
   * Final result of uploading schema, with server response or error message
   *
   * @param serverMessage message, represented as valid [[ServerMessage]]
   *                      extracted from response or plain string if response
   *                      was unexpected
   * @param status short description of outcome
   */
  case class Result(serverMessage: Either[String, ServerMessage], status: Status) {
    def asString: String =
      serverMessage match {
        case Right(message) => message.asString
        case Left(responseBody) => responseBody
      }
  }

  /**
   * Type-tag used to mark HTTP request as aiming to create apikeys
   */
  sealed trait CreateKeys

  /**
   * Type-tag used to mark HTTP request as aiming to post JSON Schema
   */
  sealed trait PostSchema

  /**
   * Type-tag used to mark URL as HTTP
   */
  sealed trait HttpUrlTag

  type HttpUrl = URL @@ HttpUrlTag

  /**
   * Transform failing [[Result]] to plain [[Result]] by inserting exception
   * message instead of server message
   *
   * @param result disjucntion of string with result
   * @return plain result
   */
  def flattenResult(result: Failing[Result]): Result =
    result match {
      case \/-(status) => status
      case -\/(failure) => Result(Left(failure), Failed)
    }

  /**
   * Extract stringified message from server response through [[ServerMessage]]
   *
   * @param response HTTP response from Iglu registry, presumably containing JSON
   * @return success message processed from JSON or error message if upload
   *         wasn't successful
   */
  def getUploadStatus(response: HttpResponse[String]): Result = {
    if (response.isSuccess)
      \/.fromTryCatch(parse(response.body).extract[ServerMessage]) match {
        case \/-(serverMessage) if serverMessage.message.contains("updated") =>
          Result(Right(serverMessage), Updated)
        case \/-(serverMessage) =>
          Result(Right(serverMessage), Created)
        case -\/(_) =>
          Result(Left(response.body), Unknown)
      }
    else {
      Result(Left(response.body), Failed)
    }
  }

  /**
   * Predicate used to filter only files which Iglu path contains `jsonschema`
   * as format
   *
   * @param file any real file
   * @return true if third entity of Iglu path is `jsonschema`
   */
  private def filterJsonSchemas(file: File): Boolean =
    file.getAbsolutePath.split(separator).takeRight(4) match {
      case Array(_, _, format, _) => format == "jsonschema"
      case _ => false
    }

  /**
   * Perform HTTP request bundled with master apikey to create and get
   * temporary read/write apikeys.
   * Performs IO
   *
   * @param request HTTP request to /api/auth/keygen authenticated by master
   *                apikey (tagged with [[CreateKeys]])
   * @return pair of apikeys for successful creation and extraction
   *         error message otherwise
   */
  def getApiKeys(request: HttpRequest @@ CreateKeys): Failing[ApiKeys] = {
    val apiKeys = for {
      response  <- \/.fromTryCatch(request.asString)
      json      <- \/.fromTryCatch(parse(response.body))
      extracted <- \/.fromTryCatch(json.extract[ApiKeys])
    } yield extracted

    apiKeys.leftMap(e => cutString(e.toString))
  }

  /**
   * Perform HTTP request bundled with temporary write key and valid
   * self-describing JSON Schema to /api/schemas/SCHEMAPATH to publish new
   * Schema.
   * Performs IO
   *
   * @param request HTTP POST-request with JSON Schema (tagged with [[PostSchema]])
   * @return successful parsed message or error message
   */
  def postSchema(request: HttpRequest @@ PostSchema): Failing[Result] =
    for {
      response <- \/.fromTryCatch(request.asString)
                    .leftMap(_.toString)
    } yield getUploadStatus(response)

  /**
   * Convert disjunction value into `EitherT`
   * Used in for-comprehensions to mimic disjunction as `Stream[String \/ A]`
   * and extract A
   */
  private def fromXor[A](value: Failing[A]): EitherT[Stream, String, A] =
    EitherT[Stream, String, A](value.point[Stream])

  /**
   * Convert stream of disjunctions into `EitherT`
   * Used in for-comprehensions to mimic disjunction as `Stream[String \/ A]`
   * and extract A
   */
  private def fromXors[A, B](value: Stream[Failing[A]]): EitherT[Stream, String, A] =
    EitherT[Stream, String, A](value)

  /**
   * Cut possibly long string (as compressed HTML) to a string with three dots
   */
  private def cutString(s: String, length: Short = 256): String = {
    val origin = s.take(length)
    if (origin.length == length) origin + "..."
    else origin
  }

  /**
   * Parse registry root (HTTP URL) from string with default `http://` protocol
   *
   * @param url string representing just host or full URL of registry root.
   *            Registry root is URL **without** /api
   * @return either error or URL tagged as HTTP in case of success
   */
  def parseRegistryRoot(url: String): Throwable \/ HttpUrl =
    \/.fromTryCatch {
      if (url.startsWith("http://") || url.startsWith("https://")) {
        Tag.of[HttpUrlTag](new URL(url.stripSuffix("/")))
      } else {
        Tag.of[HttpUrlTag](new URL("http://" + url.stripSuffix("/")))
      }
    }
}
