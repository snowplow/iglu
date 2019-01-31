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
import java.nio.file.{Path, Paths}
import java.util.UUID

import com.snowplowanalytics.iglu.core.SelfDescribingSchema

// Cats
import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.implicits._

// FS2
import fs2.Stream

// Scala
import scalaj.http.{Http, HttpRequest, HttpResponse}

// Json4s
import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.JsonMethods.{parse => jacksonParse}

// Snowplow
import com.snowplowanalytics.iglu.ctl.commands.CommonStatic._
import com.snowplowanalytics.iglu.ctl.{Common, Failing, Result => IgluctlResult}
import com.snowplowanalytics.iglu.ctl.File._
import com.snowplowanalytics.iglu.core.SelfDescribingSchema.{parse => igluParse}
import com.snowplowanalytics.iglu.core.json4s.implicits._

/**
 * Companion object, containing functions not closed on `masterApiKey`, `registryRoot`, etc
 */
object Pull {

  // json4s serialization
  private implicit val formats = DefaultFormats

  /** Primary function, performing IO reading, processing and printing results */
  def process(registryRoot: HttpUrl,
              outputDir: Path,
              masterApiKey: UUID): IgluctlResult = {

    val result = for {
      keys       <- acquireKeys(registryRoot, masterApiKey)
      responseT  = getSchemas(buildPullRequest(registryRoot, keys.read))
      responseE  = responseT.leftMap(message => Common.Error.ServiceError(message): Common.Error)
      response   <- Stream.eval[Failing, HttpResponse[String]](responseE)
      parsed     = parseResponse(response)
      schema     <- toStream(parsed)
      output     = writeSchema(schema, outputDir)
      stream     <- Stream.eval(output)
    } yield stream

    result.compile.toList.leftMap(e => NonEmptyList.of(e))
  }

  /**
    * Build HTTP GET request for all JSON Schemas and authenticated with temporary
    * read key
    *
    * @param registryRoot Iglu server URI
    * @param readKey temporary apikey allowed to read any Schema
    * @return HTTP GET request ready to be sent
    */
  def buildPullRequest(registryRoot: HttpUrl, readKey: String): HttpRequest =
    Http(s"${registryRoot.uri}/api/schemas?metadata=1")
      .header("apikey", readKey)
      .header("accept", "application/json")

  /**
    * Perform HTTP request bundled with temporary read key and valid
    * self-describing JSON Schema to /api/schemas to get all schemas.
    * Performs IO
    *
    * @param request HTTP GET-request
    * @return successful parsed message or error message
    */
  def getSchemas(request: HttpRequest): EitherT[IO, String, HttpResponse[String]] =
    EitherT(IO {
      Either.catchNonFatal(request.asString).leftMap(_.getMessage)
    }).flatMap { response =>
      if (response.code == 200) EitherT.pure[IO, String](response)
      else EitherT.leftT[IO, HttpResponse[String]](s"Unexpected status code ${response.code}. Response body: ${response.body}.")
    }

  /** Parse the response from Iglu Server
    *
    * @param response An [[HttpResponse]] returned by Iglu Server
    * @return A list of [[SelfDescribingSchema]]
    */
  def parseResponse(response: HttpResponse[String]): Either[Common.Error, List[SelfDescribingSchema[JValue]]] = {
    val parsedBody: Either[Common.Error, List[JValue]] =
      Either.catchNonFatal(jacksonParse(response.body)
        .extract[List[JValue]])
        .leftMap(error => Common.Error.Message(s"$error"))

    parsedBody
      .flatMap(_.traverse(igluParse[JValue](_)
      .leftMap(error => Common.Error.Message(error.toString))))
  }

  private def toStream[A](e: Either[Common.Error, List[A]]): Stream[Failing, A] = {
    val wrapped = EitherT.fromEither[IO](e)
    Stream.eval(wrapped).flatMap(Stream.emits)
  }

  /** Write [[SelfDescribingSchema]] as file
    * Performs IO
    *
    * @param schema A [[SelfDescribingSchema]]
    * @param outputDir The path to write to
    * @return An IO command or and Error (if a schema fails validation)
    */
  def writeSchema(schema: SelfDescribingSchema[JValue], outputDir: Path): EitherT[IO, Common.Error, String] = {
    val file = textFile(Paths.get(s"$outputDir/${schema.self.schemaKey.toPath}"), schema.asString)
    EitherT(file.write(true))
  }
}
