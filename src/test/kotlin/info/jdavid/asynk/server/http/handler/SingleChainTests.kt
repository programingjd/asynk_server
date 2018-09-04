package info.jdavid.asynk.server.http.handler

import com.fasterxml.jackson.databind.ObjectMapper
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.FixedRoute
import info.jdavid.asynk.server.http.route.NoParams
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.nio.ByteBuffer
import java.util.regex.Pattern

class SingleChainTests {

  private class TestAcceptance(method: Method, uri: String): HttpHandler.Acceptance<NoParams>(
    false, false, method, uri, NoParams
  )

  @Test fun testNoParams() {
    val handler = object: HttpHandler<TestAcceptance, NoParams, AbstractHttpHandler.Context, NoParams>(
      NoParams
    ) {
      override suspend fun context(others: Collection<*>?) = Context(others)
      override suspend fun acceptUri(method: Method, uri: String, params: NoParams): TestAcceptance? {
        if (method == Method.GET || method == Method.HEAD) {
          return TestAcceptance(method, uri)
        }
        return null
      }
      override suspend fun handle(acceptance: TestAcceptance,
                                  headers: Headers,
                                  body: ByteBuffer,
                                  context: Context): Response<*> {
        val json = mapOf(
          "method" to acceptance.method.toString(),
          "path" to acceptance.uri,
          "headers" to mapOf(*headers.keys().map { it to headers.value(it) }.toTypedArray())
        )
        val bytes = ObjectMapper().writeValueAsBytes(json)
        return ByteResponse(bytes, MediaType.JSON)
      }
    }
    Server.http(
      handler
    ).use { _ ->
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader("Test", "123")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.ACCEPT_ENCODING, "gzip")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(it.getLastHeader(
            Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.JSON))
          assertEquals(
            "{\"method\":\"GET\",\"path\":\"/\",\"headers\":{\"User-Agent\":\"Test user agent\",\"Cache-Control\":\"no-cache\",\"Pragma\":\"no-cache\",\"Test\":\"123\",\"Connection\":\"close\",\"Accept-Encoding\":\"gzip\",\"Host\":\"localhost:8080\"}}",
            String(bytes)
          )
        }
      }
    }
  }

  @Test fun testBuilderWithRoute() {
    Server.http(HttpHandler.Builder().
      route(FixedRoute("/test")).to { _, _, _, _ ->
        HttpHandler.StringResponse("Test", MediaType.TEXT)
      }.
      build()
    ).use { _ ->
      val request1 = HttpGet().apply {
        uri = URI("http://localhost:8080/test")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.ACCEPT_ENCODING, "gzip")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request1).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith("text/plain"))
          assertEquals(4, it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt())
          assertEquals(4, bytes.size)
          assertEquals("Test", String(bytes))
        }
        listOf("test/", "test/err", "abc").forEach { path ->
          val request2 = HttpGet().apply {
            uri = URI("http://localhost:8080/${path}")
            setHeader(Headers.USER_AGENT, "Test user agent")
            setHeader(Headers.CACHE_CONTROL, "no-cache")
            setHeader(Headers.PRAGMA, "no-cache")
            setHeader(Headers.CONNECTION, "close")
            setHeader(Headers.ACCEPT_ENCODING, "gzip")
          }
          client.execute(request2).use {
            assertEquals(404, it.statusLine.statusCode, path)
          }
        }
      }
    }
  }

  @Test fun testBuilder() {
    val route = object: HttpHandler.Route<Int> {
      val pattern = Pattern.compile("/([0-9])$")
      override fun match(method: Method, uri: String): Int? {
        val matcher = pattern.matcher(Uri.path(uri))
        return if (matcher.find()) matcher.group(1).toInt() else null
      }
    }
    val handler = object: HttpHandler<HttpHandler.Acceptance<Int>, Int, AbstractHttpHandler.Context, Int>(
      route
    ) {
      override suspend fun acceptUri(method: Method, uri: String, params: Int) =
        Acceptance(false, false, method, uri, params)
      override suspend fun context(others: Collection<*>?) = Context(others)
      override suspend fun handle(acceptance: Acceptance<Int>, headers: Headers, body: ByteBuffer,
                                  context: Context) =
        HttpHandler.StringResponse("${acceptance.routeParams}", MediaType.TEXT)
    }
    Server.http(HttpHandler.Builder().
      handler(handler).
      build()
    ).use { _ ->
      HttpClientBuilder.create().build().use { client ->
        listOf(3, 6, 1).forEach { n ->
          val request = HttpGet().apply {
            uri = URI("http://localhost:8080/${n}")
            setHeader(Headers.USER_AGENT, "Test user agent")
            setHeader(Headers.CACHE_CONTROL, "no-cache")
            setHeader(Headers.PRAGMA, "no-cache")
            setHeader(Headers.CONNECTION, "close")
            setHeader(Headers.ACCEPT_ENCODING, "gzip")
          }
          client.execute(request).use {
            assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith("text/plain"))
            assertEquals(1, it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt())
            assertEquals(1, bytes.size)
            assertEquals(n, String(bytes).toInt())
          }
        }
        listOf("10", "a", "").forEach { path ->
          val request = HttpGet().apply {
            uri = URI("http://localhost:8080/${path}")
            setHeader(Headers.USER_AGENT, "Test user agent")
            setHeader(Headers.CACHE_CONTROL, "no-cache")
            setHeader(Headers.PRAGMA, "no-cache")
            setHeader(Headers.CONNECTION, "close")
            setHeader(Headers.ACCEPT_ENCODING, "gzip")
          }
          client.execute(request).use {
            assertEquals(404, it.statusLine.statusCode, path)
          }
        }
      }
    }
  }

}
