package info.jdavid.asynk.server.http.handler

import com.fasterxml.jackson.databind.ObjectMapper
import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import kotlinx.coroutines.experimental.nio.aWrite
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class SingleChainTests {

  private class Acceptance(val method: Method, val uri: String): Handler.Acceptance(false, false)

  @Test fun test() {
    val handler = object: AbstractHttpHandler<Acceptance, AbstractHttpHandler.Context>() {
      override suspend fun context(others: Collection<*>?) = Context(others)
      override suspend fun acceptUri(method: Method,
                                     uri: String): Acceptance? {
        if (method == Method.GET || method == Method.HEAD) {
          return Acceptance(method, uri)
        }
        return null
      }
      override suspend fun handle(acceptance: Acceptance,
                                  headers: Headers,
                                  body: ByteBuffer,
                                  socket: AsynchronousSocketChannel,
                                  context: Context) {
        val json = mapOf(
          "method" to acceptance.method.toString(),
          "path" to acceptance.uri,
          "headers" to mapOf(*headers.keys().map { it to headers.value(it) }.toTypedArray())
        )
        val bytes = ObjectMapper().writeValueAsBytes(json)
        body.clear()
        body.put("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".
          toByteArray(Charsets.US_ASCII))
        body.put(bytes)
        //ObjectMapper().writeValue(ByteBufferBackedOutputStream(body), json)
        socket.aWrite(body.flip() as ByteBuffer)
      }
    }
    Server.http(
      handler
    ).use {
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader("Test", "123")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.ACCEPT_ENCODING, "gzip")
      }
      HttpClientBuilder.create().build().use {
        it.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(it.getLastHeader(
            Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(
            MediaType.JSON))
          assertEquals(
            "{\"method\":\"GET\",\"path\":\"/\",\"headers\":{\"User-Agent\":\"Test user agent\",\"Cache-Control\":\"no-cache\",\"Pragma\":\"no-cache\",\"Test\":\"123\",\"Connection\":\"close\",\"Accept-Encoding\":\"gzip\",\"Host\":\"localhost:8080\"}}",
            String(bytes)
          )
        }
      }
    }
  }

}
