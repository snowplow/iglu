package com.snowplowanalytics.iglu.server
package service

import java.util.UUID

import cats.implicits._
import cats.effect.IO

import fs2.Stream

import io.circe._
import io.circe.literal._

import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.rho.swagger.syntax.io.createRhoMiddleware

import com.snowplowanalytics.iglu.core.{SchemaMap, SchemaVer, SchemaKey, SelfDescribingSchema}
import com.snowplowanalytics.iglu.server.codecs.JsonCodecs._
import com.snowplowanalytics.iglu.server.model.{ IgluResponse, Schema }


class SchemaServiceSpec extends org.specs2.Specification { def is = s2"""
  GET
    Returns 404 for non-existing schema $e1
    Returns 200 and schema for existing public schema $e3
    Returns 200 and schema for existing private schema $e4
    Returns list of schemas with metadata if metadata=1 passed $e9
    Returns list of canonical schemas if body=1 passed $e10
    Returns 404 for existing schema if invalid apikey provided $e5
    Returns only public schemas without apikey $e7
    Returns public and private schemas with apikey $e8
    Returns error for non-UUID apikey $e6
    Returns 404 and meaningful error for invalid SchemaVer $e14
    Returns schemas ordered by pub_date $e15
  PUT
    Prohibits adding new schema if it already exists $e11
    Prohibits adding new schema if previous version does not exist $e12
    Prohibits adding new schema if previous version belongs to different apikey
    PUT request adds schema $e2
    PUT request updates existing schema if patches are allowed $e13
  """

  def e1 = {
    val req: Request[IO] =
      Request(Method.GET, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))

    val response = SchemaServiceSpec.request(List(req), false)
    response.unsafeRunSync().status must beEqualTo(Status.NotFound)
  }

  def e3 = {
    val req: Request[IO] =
      Request(Method.GET, Uri.uri("/com.acme/event/jsonschema/1-0-0"))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[Json]
    } yield (response.status, body)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.Ok) and (body must beEqualTo(SpecHelpers.selfSchemaZero))
  }

  def e4 = {
    val req: Request[IO] =
      Request(
        Method.GET,
        Uri.uri("/com.acme/secret/jsonschema/1-0-0"),
        headers = Headers.of(Header("apikey", SpecHelpers.readKeyAcme.toString)))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[Json]
    } yield (response.status, body)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.Ok) and (body must beEqualTo(SpecHelpers.selfSchemaPrivate))
  }

  def e5 = {
    val req: Request[IO] =
      Request(
        Method.GET,
        Uri.uri("/com.acme/secret/jsonschema/1-0-0"),
        headers = Headers.of(Header("apikey", UUID.randomUUID().toString)))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[IgluResponse]
    } yield (response.status, body)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.NotFound) and (body must beEqualTo(IgluResponse.SchemaNotFound))
  }

  def e9 = {
    val req: Request[IO] =
      Request(
        Method.GET,
        Uri.uri("/").withQueryParam("metadata", "1"))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[List[Schema]]
    } yield (response.status, body)

    val expectedBody = SpecHelpers.schemas
      .filter { case (_, m) => m.metadata.isPublic }
      .map(_._2)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.Ok) and (body must beEqualTo(expectedBody))
  }

  def e10 = {
    val req: Request[IO] =
      Request(
        Method.GET,
        Uri.uri("/").withQueryParam("body", "1"))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[List[SelfDescribingSchema[Json]]]
    } yield (response.status, body.map(_.self))

    val expectedBody = SpecHelpers.schemas
      .filter { case (_, m) => m.metadata.isPublic }
      .map(_._2.schemaMap)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.Ok) and (body must beEqualTo(expectedBody))
  }


  def e6 = {
    val req: Request[IO] =
      Request(
        Method.GET,
        Uri.uri("/com.acme/secret/jsonschema/1-0-0"),
        headers = Headers.of(Header("apikey", "not-uuid")))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[Json]
    } yield (response.status, body)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.BadRequest) and (body.noSpaces must contain("Invalid UUID"))
  }

  def e7 = {
    val req: Request[IO] =
      Request(Method.GET, Uri.uri("/"))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[List[SchemaKey]]
    } yield (response.status, body)

    val expected = List(
      SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,0)),
      SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,1))
    )
    val (status, body) = result.unsafeRunSync()

    status must beEqualTo(Status.Ok) and (body must beEqualTo(expected))
  }

  def e8 = {
    val req: Request[IO] =
      Request(Method.GET, uri"/")
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[List[SchemaKey]]
    } yield (response.status, body)

    val expected = List(
      SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,0)),
      SchemaKey("com.acme", "event", "jsonschema", SchemaVer.Full(1,0,1)),
      SchemaKey("com.acme", "secret", "jsonschema", SchemaVer.Full(1,0,0))
    )
    val (status, body) = result.unsafeRunSync()

    status must beEqualTo(Status.Ok) and (body must beEqualTo(expected))
  }

  def e2 = {
    val selfDescribingSchema =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object"
        }"""
    val exampleSchema = Stream.emits(selfDescribingSchema.noSpaces.stripMargin.getBytes).covary[IO]

    val reqs: List[Request[IO]] = List(
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/1-0-0")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.GET, uri"/com.acme/nonexistent/jsonschema/1-0-0")
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
    )

    val (requests, state) = SchemaServiceSpec.state(reqs, false).unsafeRunSync()
    val dbExpectation = state.schemas.mapValues(s => (s.metadata.isPublic, s.body)) must havePair(
      (SchemaMap("com.acme", "nonexistent", "jsonschema", SchemaVer.Full(1,0,0)), (false, json"""{"type": "object"}"""))
    )
    val requestExpectation = requests.lastOption.map(_.status) must beSome(Status.Ok)
    dbExpectation and requestExpectation
  }

  def e11 = {
    val selfDescribingSchema =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object"
        }"""
    val exampleSchema = Stream.emits(selfDescribingSchema.noSpaces.stripMargin.getBytes).covary[IO]

    val selfDescribingSchemaUpdated =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object",
          "additionalProperties": true
        }"""
    val exampleSchemaUpdated = Stream.emits(selfDescribingSchemaUpdated.noSpaces.stripMargin.getBytes).covary[IO]

    val reqs: List[Request[IO]] = List(
      Request[IO](Method.PUT, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchemaUpdated),
      Request[IO](Method.GET, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
    )

    val (requests, state) = SchemaServiceSpec.state(reqs, false).unsafeRunSync()
    val dbExpectation = state.schemas.mapValues(s => (s.metadata.isPublic, s.body)) must havePair(
      (SchemaMap("com.acme", "nonexistent", "jsonschema", SchemaVer.Full(1,0,0)), (false, json"""{"type": "object"}"""))
    )
    val putRequestExpectation = requests.get(1).map(_.status) must beSome(Status.Conflict)
    val getRequestExpectation = requests.lastOption.map(_.status) must beSome(Status.Ok)
    dbExpectation and putRequestExpectation and getRequestExpectation
  }

  def e12 = {
    val selfDescribingSchema =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-2-0"
          },
          "type": "object"
        }"""
    val exampleSchema = Stream.emits(selfDescribingSchema.noSpaces.stripMargin.getBytes).covary[IO]

    val req = Request[IO](Method.PUT, Uri.uri("/com.acme/nonexistent/jsonschema/1-2-0"))
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema)

    val result = for {
      response <- SchemaServiceSpec.request(List(req), false)
      body <- response.as[Json]
    } yield (response.status, body)

    val (status, body) = result.unsafeRunSync()
    status must beEqualTo(Status.Conflict) and (body.noSpaces must contain("Preceding SchemaVer in the group is missing, check that schemas published in proper order"))
  }

  def e13 = {
    val selfDescribingSchema =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object"
        }"""
    val exampleSchema = Stream.emits(selfDescribingSchema.noSpaces.stripMargin.getBytes).covary[IO]

    val selfDescribingSchemaUpdated =
      json"""
        {
          "self": {
            "vendor": "com.acme",
            "name": "nonexistent",
            "format": "jsonschema",
            "version": "1-0-0"
          },
          "type": "object",
          "additionalProperties": true
        }"""
    val exampleSchemaUpdated = Stream.emits(selfDescribingSchemaUpdated.noSpaces.stripMargin.getBytes).covary[IO]

    val reqs: List[Request[IO]] = List(
      Request[IO](Method.PUT, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchemaUpdated),
      Request[IO](Method.GET, Uri.uri("/com.acme/nonexistent/jsonschema/1-0-0"))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
    )

    val (requests, state) = SchemaServiceSpec.state(reqs, true).unsafeRunSync()
    val dbExpectation = state.schemas.mapValues(s => (s.metadata.isPublic, s.body)) must havePair(
      (SchemaMap("com.acme", "nonexistent", "jsonschema", SchemaVer.Full(1,0,0)), (false, json"""{"type": "object", "additionalProperties": true}"""))
    )
    val requestExpectation = requests.lastOption.map(_.status) must beSome(Status.Ok)
    dbExpectation and requestExpectation
  }

  def e14 = {
    val req: Request[IO] =
      Request(Method.GET, uri"/com.acme/nonexistent/jsonschema/boom")

    val response = SchemaServiceSpec.request(List(req), false)

    val result = for {
      r <- response
      body <- r.bodyAsText.compile.foldMonoid
    } yield (r.status, body)

    // Text body transformed to JSON later in HttpApp
    val (status, body) = result.unsafeRunSync()
    (status must beEqualTo(Status.BadRequest)) and (body must beEqualTo("Cannot parse 'boom' as SchemaVer, INVALID_SCHEMAVER"))
  }

  def e15 = {
    val simpleSchema = json"""{"type": "object"}"""
    val exampleSchema = Stream.emits(simpleSchema.noSpaces.stripMargin.getBytes).covary[IO]

    val reqs: List[Request[IO]] = List(
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/1-0-0")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/1-0-1")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/2-0-0")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/1-0-2")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.PUT, uri"/com.acme/nonexistent/jsonschema/1-1-0")
        .withContentType(headers.`Content-Type`(MediaType.application.json))
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
        .withBodyStream(exampleSchema),
      Request[IO](Method.GET, uri"/com.acme/nonexistent/jsonschema/1")
        .withHeaders(Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))
    )

    val expected =
      """["iglu:com.acme/nonexistent/jsonschema/1-0-0","iglu:com.acme/nonexistent/jsonschema/1-0-1","iglu:com.acme/nonexistent/jsonschema/1-0-2","iglu:com.acme/nonexistent/jsonschema/1-1-0"]"""

    val result = for {
      response <- SchemaServiceSpec.request(reqs, false)
      last <- response.bodyAsText.compile.foldMonoid
    } yield last

    result.unsafeRunSync() must beEqualTo(expected)
  }
}

object SchemaServiceSpec {
  import storage.InMemory

  val client: Client[IO] = Client.fromHttpApp(HttpApp[IO](r => Response[IO]().withEntity(r.body).pure[IO]))

  def request(reqs: List[Request[IO]], patchesAllowed: Boolean): IO[Response[IO]] = {
    for {
      storage <- InMemory.getInMemory[IO](SpecHelpers.exampleState)
      service = SchemaService.asRoutes(patchesAllowed, Webhook.WebhookClient(List(), client))(storage, SpecHelpers.ctx, createRhoMiddleware())
      responses <- reqs.traverse(service.run).value
    } yield responses.flatMap(_.lastOption).getOrElse(Response(Status.NotFound))
  }

  def state(reqs: List[Request[IO]], patchesAllowed: Boolean): IO[(List[Response[IO]], InMemory.State)] = {
    for {
      storage <- InMemory.getInMemory[IO](SpecHelpers.exampleState)
      service = SchemaService.asRoutes(patchesAllowed, Webhook.WebhookClient(List(), client))(storage, SpecHelpers.ctx, createRhoMiddleware())
      responses <- reqs.traverse(service.run).value
      state <- storage.ref.get
    } yield (responses.getOrElse(List.empty), state)
  }
}
