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
import org.json4s.{DefaultFormats, JValue}
import com.snowplowanalytics.iglu.ctl.commands.Push.{HttpUrl, buildCreateKeysRequest, deleteKey, getApiKeys}
import com.snowplowanalytics.iglu.ctl.{Common, Result => IgluctlResult}
import com.snowplowanalytics.iglu.ctl.File._
import fs2.Stream
import org.json4s.jackson.JsonMethods.{parse => jacksonParse}
import scalaj.http.{Http, HttpRequest, HttpResponse}
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


  /*
  val schemas = HttpResponse("""[{"a":}, {"$schema":"http://json-schema.org/draft-04/schema#","description":"Meta-schema for self-describing JSON schema","self":{"vendor":"com.snowplowanalytics.self-desc","name":"schema","format":"jsonschema","version":"1-0-0"},"allOf":[{"properties":{"self":{"type":"object","properties":{"vendor":{"type":"string","pattern":"^[a-zA-Z0-9-_.]+$"},"name":{"type":"string","pattern":"^[a-zA-Z0-9-_]+$"},"format":{"type":"string","pattern":"^[a-zA-Z0-9-_]+$"},"version":{"type":"string","pattern":"^[0-9]+-[0-9]+-[0-9]+$"}},"required":["vendor","name","format","version"],"additionalProperties":false}},"required":["self"]},{"$ref":"http://json-schema.org/draft-04/schema#"}],"metadata":{"location":"/api/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0","createdAt":"2019-01-18T17:25:06.341","updatedAt":"2019-01-18T17:25:06.341","permissions":{"read":"public","write":"none"}}},{"self":{"vendor":"com.clearbit.reveal","name":"company","format":"jsonschema","version":"1-0-0"},"$schema":"http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#","type":"object","properties":{"ip":{"type":["string","null"],"description":"IP address that was looked up"},"fuzzy":{"type":"boolean","description":"False if the company has their own dedicated ASN block, otherwise true."},"domain":{"type":["string","null"],"description":"The matched company domain"},"type":{"type":["string","null"],"description":"The type of result (company, education, government, isp)"},"geoIP":{"type":"object","description":"A object containing location data for lookup IP","properties":{"city":{"type":["string","null"],"description":"City that this IP is located in"},"state":{"type":["string","null"],"description":"State that this IP is located in"},"stateCode":{"type":["string","null"],"description":"State code for this IP’s state"},"country":{"type":["string","null"],"description":"Country that this IP is located in"},"countryCode":{"type":["string","null"],"description":"Country code for this IP’s country"}},"additionalProperties":true},"company":{"type":"object","description":"A full company object","properties":{"id":{"type":["string","null"],"format":"uuid","description":"Internal ID"},"name":{"type":["string","null"],"description":"Name of company"},"legalName":{"type":["string","null"],"description":"Legal name of company"},"domain":{"type":["string","null"],"description":"Domain of company’s website"},"domainAliases":{"type":"array","description":"List of domains also used by the company","items":{"type":"string"}},"phone":{"type":["string","null"],"description":"International headquarters phone number"},"site":{"type":"object","properties":{"emailAddresses":{"type":"array","description":"List of email addresses mentioned on the company’s website","items":{"type":"string"}},"phoneNumbers":{"type":"array","description":"List of phone numbers mentioned on the company’s website","items":{"type":"string"}}},"additionalProperties":true},"category":{"type":"object","properties":{"sector":{"type":["string","null"],"description":"Broad sector"},"industryGroup":{"type":["string","null"],"description":"Industry group"},"industry":{"type":["string","null"],"description":"Industry"},"subIndustry":{"type":["string","null"],"description":"Sub industry"},"sicCode":{"type":["string","null"],"description":"Two digit category SIC code"},"naicsCode":{"type":["string","null"],"description":"Two digit category NAICS code"}},"additionalProperties":true},"tags":{"type":"array","description":"List of market categories","items":{"type":["string","null"]}},"description":{"type":["string","null"],"description":"Description of the company"},"type":{"type":["string","null"],"description":"The company’s type, either education, government,  nonprofit, private, public, or personal."},"tech":{"type":"array","description":"Array of technology tags","items":{"type":["string","null"]}},"foundedYear":{"type":["string","null"],"description":"Year company was founded"},"location":{"type":["string","null"],"description":"Address of company"},"timeZone":{"type":["string","null"],"description":"The timezone for the company’s location"},"utcOffset":{"type":["integer","null"],"maximum":32767,"minimum":-32768,"description":"The offset from UTC in hours in the company’s location"},"geo":{"type":"object","properties":{"city":{"type":["string","null"],"description":"Headquarters city name"},"stateCode":{"type":["string","null"],"description":"Headquarters two character state code"},"lng":{"type":["number","null"],"minimum":-180,"maximum":180,"description":"Headquarters longitude"},"state":{"type":["string","null"],"description":"Headquarters state name"},"country":{"type":["string","null"],"description":"Headquarters country name"},"streetName":{"type":["string","null"],"description":"Headquarters street name"},"postalCode":{"type":["string","null"],"description":"Headquarters postal/zip code"},"subPremise":{"type":["string","null"],"description":"Headquarters suite number"},"countryCode":{"type":["string","null"],"description":"Headquarters two character country code"},"streetNumber":{"type":["string","null"],"description":"Headquarters street number"},"lat":{"type":["number","null"],"minimum":-90,"maximum":90,"description":"Headquarters latitude"}},"additionalProperties":true},"metrics":{"type":"object","properties":{"employees":{"type":["integer","null"],"maximum":2147483647,"minimum":0,"description":"Amount of employees"},"employeesRange":{"type":["string","null"],"description":"Employees range"},"marketCap":{"type":["integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Market Cap"},"raised":{"type":["integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Total amount raised"},"alexaUsRank":{"type":["integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Alexa’s US site rank"},"alexaGlobalRank":{"type":["integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Alexa’s global site rank"},"annualRevenue":{"type":["integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Annual Revenue (public companies only)"},"estimatedAnnualRevenue":{"type":["string","null"],"description":"Estimated annual revenue range"},"fiscalYearEnd":{"type":["string","null"],"description":"Month that the fiscal year ends (1-indexed)"}},"additionalProperties":true},"logo":{"type":["string","null"],"format":"uri","description":"SRC of company logo"},"facebook":{"type":"object","properties":{"handle":{"type":["string","null"],"description":"Company’s Facebook ID"}},"additionalProperties":true},"linkedin":{"type":"object","properties":{"handle":{"type":["string","null"],"description":"Company’s Linkedin URL"}},"additionalProperties":true},"twitter":{"type":"object","properties":{"avatar":{"type":["string","null"],"format":"uri","description":"HTTP Twitter avatar"},"location":{"type":["string","null"],"description":"Twitter location"},"followers":{"type":["integer","null"],"maximum":2147483647,"minimum":0,"description":"Count of Twitter followers"},"site":{"type":["string","null"],"format":"uri","description":"Twitter site"},"following":{"type":["integer","null"],"maximum":2147483647,"minimum":0,"description":"Count of Twitter friends"},"bio":{"type":["string","null"],"description":"Twitter Bio"},"id":{"type":["string","integer","null"],"maximum":9223372036854775807,"minimum":0,"description":"Twitter ID"},"handle":{"type":["string","null"],"description":"Twitter screen name"}},"additionalProperties":true},"crunchbase":{"type":"object","properties":{"handle":{"type":["string","null"],"description":"Crunchbase handle"}},"additionalProperties":true},"identifiers":{"type":"object","properties":{"usEIN":{"type":["string","null"],"description":"US Employer Identification Number"}},"additionalProperties":true},"emailProvider":{"type":"boolean","description":"is the domain associated with a free email provider (i.e. Gmail)?"},"indexedAt":{"type":["string","null"],"description":"The time at which we indexed this data"},"parent":{"type":"object","properties":{"domain":{"type":["string","null"],"description":"The domain of the parent company (if any)"}},"additionalProperties":true}},"additionalProperties":true}},"additionalProperties":true,"metadata":{"location":"/api/schemas/com.clearbit.reveal/company/jsonschema/1-0-0","createdAt":"2019-01-21T11:07:42.604","updatedAt":"2019-01-21T11:07:42.604","permissions":{"read":"private","write":"none"}}}]""", 200, Map("headerkey" -> IndexedSeq("headervalue")))
  val output = """~/Dev/github/"""
  val written = writeSchemas(schemas, Paths.get(output))
  */
}
