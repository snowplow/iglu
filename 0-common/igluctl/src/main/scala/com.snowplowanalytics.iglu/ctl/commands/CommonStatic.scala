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

package com.snowplowanalytics.iglu.ctl.commands

// Java
import java.net.URI
import java.util.UUID

// FS2
import fs2.Stream

// Cats
import cats.data.{EitherT, Validated}
import cats.effect.IO
import cats.implicits._

// Json4s
import org.json4s.jackson.JsonMethods.parse
import org.json4s.DefaultFormats

// Scala
import scalaj.http.{Http, HttpRequest}

// Snowplow
import com.snowplowanalytics.iglu.ctl.Failing
import com.snowplowanalytics.iglu.ctl.Common.Error

/**
  * Object containing functions common to the static [[Push]] and [[Pull]] commands
  */
object CommonStatic {

  // json4s serialization
  private implicit val formats = DefaultFormats

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

  def acquireKeys(registryRoot: HttpUrl, masterApiKey: UUID): Stream[Failing, ApiKeys] = {
    val createKeysRequest = buildCreateKeysRequest(registryRoot, masterApiKey)
    Stream.bracket(getApiKeys(createKeysRequest)) { keys =>
      val deleteRead = deleteKey(registryRoot, masterApiKey, keys.read, "read")
      val deleteWrite = deleteKey(registryRoot, masterApiKey, keys.write, "write")
      EitherT.liftF(deleteWrite *> deleteRead)
    }
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