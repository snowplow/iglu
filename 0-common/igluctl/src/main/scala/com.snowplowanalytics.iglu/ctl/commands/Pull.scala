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

import java.nio.file.{Path, Paths}
import java.util.UUID

import cats.data.{EitherT, NonEmptyList}
import cats.effect.IO
import cats.implicits._

import fs2.Stream

import scalaj.http.{Http, HttpRequest, HttpResponse}

import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.JsonMethods.{parse => jacksonParse}

import com.snowplowanalytics.iglu.ctl.commands.Push.{HttpUrl, buildCreateKeysRequest, deleteKey, getApiKeys} // TODO: Spin off into separate package object so we don't have to import from Push
import com.snowplowanalytics.iglu.ctl.{Common, Result => IgluctlResult}
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
    val createKeysRequest = buildCreateKeysRequest(registryRoot, masterApiKey)
    val acquireKeys = Stream.bracket(getApiKeys(createKeysRequest)) { keys =>
      val deleteRead = deleteKey(registryRoot, masterApiKey, keys.read, "read")
      val deleteWrite = deleteKey(registryRoot, masterApiKey, keys.write, "write")
      EitherT.liftF(deleteWrite *> deleteRead)
    }

    val schemas = (for {
      keys <- acquireKeys
    } yield getSchemas(buildPullRequest(registryRoot, keys.read)))
      .compile.toList.getOrElse(throw new RuntimeException("Could not get schema."))
      .map(_.map(_.value)).map(_.traverse(effect => EitherT(effect)).value).flatten

    val result = schemas.map(_.getOrElse(throw new RuntimeException("Could not write schema.")) //Should this be throwing an exception? If 1 out of 10 schemas does not validate, should we not write the other 9 and silently drop the invalid one?
      .map(response => writeSchemas(response, outputDir).value)
      .sequence[IO, Either[Common.Error, List[String]]])
      .map(_.map(_.traverse(x => x).map(_.flatten).leftMap(error => NonEmptyList.of(error)))).flatten

    EitherT(result)
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
      else EitherT.leftT[IO, HttpResponse[String]]("Status code is not 200.")
    }

  def writeSchemas(schemas: HttpResponse[String], output: Path): EitherT[IO, Common.Error, List[String]] = {
    val parsed = jacksonParse(schemas.body).extract[List[JValue]].map(igluParse[JValue](_).getOrElse(throw new RuntimeException("Invalid self-describing JSON schema"))).map {
      schema => textFile(Paths.get(s"$output/${schema.self.schemaKey.toPath}"), schema.asString)
    }
    parsed.map(_.write(true)).traverse(effect => EitherT(effect))
  }
}
