package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.route.FixedRoute
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI

class BuilderTests {

  @Test
  fun testBuilder() {
    val handler = HttpHandler.Builder().
      route("/fixed").to { _ , _, _, _ ->
        HttpHandler.StringResponse("fixed", MediaType.TEXT)
      }.
      route("/param/{p1}").to { acceptance, _, _, _ ->
        HttpHandler.StringResponse(acceptance.routeParams["p1"] ?: "?", MediaType.TEXT)
      }.
      route(object: HttpHandler.Route<Boolean> {
        override fun match(method: Method, uri: String) = if (Uri.path(uri) == "/route") true else null
        override val maxRequestSize: Int = 4096
      }).to { acceptance, _, _, _ ->
        HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
      }.
      handler(HttpHandler.of(
        FixedRoute("/handler")
      ) { _ , _, _, _ ->
          HttpHandler.StringResponse("handler", MediaType.TEXT)
        }
      ).
      build()
    Server.http(
      handler
    ).use { _ ->
      val request = HttpGet().apply {
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.ACCEPT_ENCODING, "gzip")
      }
      HttpClientBuilder.create().build().use { client ->
        request.apply {
          uri = URI("http://localhost:8080/fixed")
        }
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(
            "fixed",
            String(bytes)
          )
        }
        request.apply {
          uri = URI("http://localhost:8080/param/param1")
        }
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(
            "param1",
            String(bytes)
          )
        }
        request.apply {
          uri = URI("http://localhost:8080/route")
        }
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(
            "true",
            String(bytes)
          )
        }
        request.apply {
          uri = URI("http://localhost:8080/handler")
        }
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(
            "handler",
            String(bytes)
          )
        }
      }
    }
  }

}
