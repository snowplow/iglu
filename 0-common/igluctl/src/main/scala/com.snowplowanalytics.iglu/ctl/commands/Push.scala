/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
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
package commands

import java.net.URI
import java.nio.file.Path
import java.util.UUID

import cats.data.{EitherT, Validated}
import cats.effect.IO
import cats.implicits._

import fs2.Stream

import scalaj.http.{Http, HttpRequest, HttpResponse}

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import com.snowplowanalytics.iglu.core.json4s.implicits._
import com.snowplowanalytics.iglu.schemaddl.IgluSchema
import com.snowplowanalytics.iglu.ctl.File.{filterJsonSchemas, streamFiles}
import com.snowplowanalytics.iglu.ctl.Common.Error
import com.snowplowanalytics.iglu.ctl.{ Result => IgluctlResult }

/**
 * Companion objects, containing functions not closed on `masterApiKey`, `registryRoot`, etc
 */
object Push {

  // json4s serialization
  private implicit val formats = DefaultFormats

  /** Primary function, performing IO reading, processing and printing results */
  def process(inputDir: Path,
              registryRoot: HttpUrl,
              masterApiKey: UUID,
              isPublic: Boolean): IgluctlResult = {
    val createKeysRequest = buildCreateKeysRequest(registryRoot, masterApiKey)
    val acquireKeys = Stream.bracket(getApiKeys(createKeysRequest)) { keys =>
      val deleteRead = deleteKey(registryRoot, masterApiKey, keys.read, "read")
      val deleteWrite = deleteKey(registryRoot, masterApiKey, keys.write, "write")
      EitherT.liftF(deleteWrite *> deleteRead)
    }

    val stream = for {
      keys   <- acquireKeys
      file   <- streamFiles(inputDir, Some(filterJsonSchemas)).translate[IO, Failing](Common.liftIO).map(_.flatMap(_.asJsonSchema))
      result <- file match {
        case Right(schema) =>
          val request = buildRequest(registryRoot, isPublic, schema.content, keys.write)
          Stream.eval[Failing, Result](postSchema(request))
        case Left(error) =>
          Stream.eval(EitherT.leftT[IO, Result](error))
      }
      _      <- Stream.eval[Failing, Unit](EitherT.liftF(IO(println(result.asString))))
    } yield ()

    EitherT(stream.compile.drain.value.map(_.toEitherNel.as(Nil)))
  }

  /**
    * Build HTTP POST-request with master apikey to create temporary
    * read/write apikeys
    *
    * @return HTTP POST-request ready to be sent
    */
  def buildCreateKeysRequest(registryRoot: HttpUrl, masterApiKey: UUID): HttpRequest =
    Http(s"${registryRoot.uri}/api/auth/keygen")
      .header("apikey", masterApiKey.toString)
      .postForm(List(("vendor_prefix", "*")))

  /**
    * Build HTTP POST-request with JSON Schema and authenticated with temporary
    * write key
    *
    * @param schema valid self-describing JSON Schema
    * @param writeKey temporary apikey allowed to write any Schema
    * @return HTTP POST-request ready to be sent
    */
  def buildRequest(registryRoot: HttpUrl, isPublic: Boolean, schema: IgluSchema, writeKey: String): HttpRequest =
    Http(s"${registryRoot.uri}/api/schemas/${schema.self.schemaKey.toPath}")
      .header("apikey", writeKey)
      .param("isPublic", isPublic.toString)
      .put(schema.asString)

  /**
    * Send DELETE request for temporary key.
    * Performs IO
    *
    * @param key UUID of temporary key
    * @param purpose what exact key being deleted, used to log, can be empty
    */
  def deleteKey(registryRoot: HttpUrl, masterApiKey: UUID, key: String, purpose: String): IO[Unit] = {
    val request = Http(s"$registryRoot/api/auth/keygen")
      .header("apikey", masterApiKey.toString)
      .param("key", key)
      .method("DELETE")

    IO(Validated.catchNonFatal(request.asString) match {
      case Validated.Valid(response) if response.isSuccess => println(s"$purpose key $key deleted")
      case Validated.Valid(response) => println(s"FAILURE: DELETE $purpose $key response: ${response.body}")
      case Validated.Invalid(throwable) => println(s"FAILURE: $purpose $key: ${throwable.toString}")
    })
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
      else ()
    }

    /**
      * Modify end-of-the-world object, by sinking reports and printing info
      * Performs IO
      *
      * @param result result of upload
      * @return new modified total object
      */
    def add(result: Result): Total = result match {
      case s @ Result(_, Status.Updated) =>
        println(s"SUCCESS: ${s.asString}")
        copy(updates = updates + 1)
      case s @ Result(_, Status.Created) =>
        println(s"SUCCESS: ${s.asString}")
        copy(creates = creates + 1)
      case s @ Result(_, Status.Failed) =>
        println(s"FAILURE: ${s.asString}")
        copy(failures = failures + 1)
      case s @ Result(_, Status.Unknown) =>
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
  case class ServerMessage(status: Int, message: String, location: Option[String])
  object ServerMessage {
    def asString(status: Int, message: String, location: Option[String]): String =
      s"$message ${location.map("at " + _ + " ").getOrElse("")} ($status)"
  }

  /**
   * ADT representing all possible statuses for Schema upload
   */
  sealed trait Status extends Serializable
  object Status {
    case object Updated extends Status
    case object Created extends Status
    case object Unknown extends Status
    case object Failed extends Status
  }

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
        case Right(message) => ServerMessage.asString(message.status, message.message, message.location)
        case Left(responseBody) => responseBody
      }
  }

  /**
   * Transform failing [[Result]] to plain [[Result]] by inserting exception
   * message instead of server message
   *
   * @param result disjucntion of string with result
   * @return plain result
   */
  def flattenResult(result: Either[String, Result]): Result =
    result match {
      case Right(status) => status
      case Left(failure) => Result(Left(failure), Status.Failed)
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
      Either.catchNonFatal(parse(response.body).extract[ServerMessage]) match {
        case Right(serverMessage) if serverMessage.message.contains("updated") =>
          Result(Right(serverMessage), Status.Updated)
        case Right(serverMessage) =>
          Result(Right(serverMessage), Status.Created)
        case Left(_) =>
          Result(Left(response.body), Status.Unknown)
      }
    else {
      Result(Left(response.body), Status.Failed)
    }
  }

  /**
   * Perform HTTP request bundled with master apikey to create and get
   * temporary read/write apikeys.
   * Performs IO
   *
   * @param request HTTP request to /api/auth/keygen authenticated by master apikey
   * @return pair of apikeys for successful creation and extraction
   *         error message otherwise
   */
  def getApiKeys(request: HttpRequest): Failing[ApiKeys] = {
    val apiKeys = for {
      response  <- EitherT.liftF(IO(request.asString))
      json      <- EitherT.fromEither[IO](Either.catchNonFatal(parse(response.body)))
      extracted <- EitherT.fromEither[IO](Either.catchNonFatal(json.extract[ApiKeys]))
    } yield extracted

    apiKeys.leftMap(e => Error.ServiceError(cutString(e.getMessage)))
  }

  /**
   * Perform HTTP request bundled with temporary write key and valid
   * self-describing JSON Schema to /api/schemas/SCHEMAPATH to publish new
   * Schema.
   * Performs IO
   *
   * @param request HTTP POST-request with JSON Schema
   * @return successful parsed message or error message
   */
  def postSchema(request: HttpRequest): Failing[Result] =
    EitherT.liftF(IO(request.asString).map(getUploadStatus))

  /**
   * Cut possibly long string (as compressed HTML) to a string with three dots
   */
  private def cutString(s: String, length: Short = 256): String = {
    val origin = s.take(length)
    if (origin.length == length) origin + "..."
    else origin
  }

  case class HttpUrl(uri: URI) extends AnyVal {
    override def toString: String = uri.toString
  }

  object HttpUrl {
    /**
      * Parse registry root (HTTP URL) from string with default `http://` protocol
      * @param url string representing just host or full URL of registry root.
      *            Registry root is URL **without** /api
      * @return either error or URL tagged as HTTP in case of success
      */
    def parse(url: String): Either[Error, HttpUrl] =
      Either.catchNonFatal {
        if (url.startsWith("http://") || url.startsWith("https://")) {
          HttpUrl(new URI(url.stripSuffix("/")))
        } else {
          HttpUrl(new URI("http://" + url.stripSuffix("/")))
        }
      }.leftMap(error => Error.ConfigParseError(error.getMessage))
  }
}
