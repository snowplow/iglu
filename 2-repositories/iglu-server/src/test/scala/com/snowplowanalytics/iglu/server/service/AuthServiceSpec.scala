package com.snowplowanalytics.iglu.server
package service

import cats.effect.IO
import fs2.Stream
import org.http4s._
import org.http4s.rho.swagger.syntax.io.createRhoMiddleware

class AuthServiceSpec extends org.specs2.Specification { def is = s2"""
  /keygen generates read-only API key pair for master key and JSON payload $e1
  /keygen generates read-only API key pair for master key and form payload (deprecated) $e2
  /keygen doesn't authorize without apikey header $e3
  /keygen deletes key $e4
  """

  def e1 = {
    import model._

    val req = Request(Method.POST,
      Uri.uri("/keygen"),
      headers = Headers.of(Header("apikey", SpecHelpers.masterKey.toString)),
      body = Stream.emits("""{"vendorPrefix": "me.chuwy"}""").evalMap(c => IO.pure(c.toByte)))

    val expected = Permission(
      Permission.Vendor(List("me", "chuwy"),true),
      Some(Permission.SchemaAction.Read),
      Set()
    )

    val response = AuthServiceSpec.state(List(req))
    val (_, state) = response.unsafeRunSync()
    state.permission must haveValues(expected)
  }

  def e2 = {
    import model._

    val req = Request(Method.POST,
      Uri.uri("/keygen"),
      headers = Headers.of(Header("apikey", SpecHelpers.masterKey.toString)),
      body = Stream.emits("""vendor_prefix=ru.chuwy""").evalMap(c => IO.pure(c.toByte))
    ).withContentType(headers.`Content-Type`(MediaType.application.`x-www-form-urlencoded`))

    val expected = Permission(
      Permission.Vendor(List("ru", "chuwy"),true),
      Some(Permission.SchemaAction.Read),
      Set()
    )

    val response = AuthServiceSpec.state(List(req))
    val (_, state) = response.unsafeRunSync()
    state.permission must haveValues(expected)
  }

  def e3 = {
    val req = Request(Method.POST,
      Uri.uri("/keygen"),
      body = Stream.emits("""{"vendorPrefix": "me.chuwy"}""").evalMap(c => IO.pure(c.toByte)))

    val response = AuthServiceSpec.state(List(req))
    val (responses, state) = response.unsafeRunSync()
    val stateHaventChanged = state must beEqualTo(SpecHelpers.exampleState)
    val unauthorized = responses.map(_.status) must beEqualTo(List(Status.Forbidden))

    stateHaventChanged and unauthorized
  }

  def e4 = {
    val req = Request[IO](Method.DELETE,
      Uri.uri("/keygen").withQueryParam("key", SpecHelpers.readKey.toString),
      headers = Headers.of(Header("apikey", SpecHelpers.masterKey.toString)))

    val response = AuthServiceSpec.state(List(req))
    val (responses, state) = response.unsafeRunSync()
    val nokey = state.permission must not haveKey(SpecHelpers.readKey)
    val deletedResponse = responses.map(_.status) must beEqualTo(List(Status.Ok))

    nokey and deletedResponse
  }
}

object AuthServiceSpec {
  val state =
    SpecHelpers.state(storage => AuthService.asRoutes(storage, SpecHelpers.ctx, createRhoMiddleware())) _
}
