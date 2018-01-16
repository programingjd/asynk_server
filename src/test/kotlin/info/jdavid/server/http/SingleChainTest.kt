package info.jdavid.server.http

import com.fasterxml.jackson.databind.ObjectMapper
import info.jdavid.server.Handler
import info.jdavid.server.Server
import kotlinx.coroutines.experimental.nio.aWrite
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class SingleChainTest {

  private class Acceptance(val method: Method, val uri: String): Handler.Acceptance(false, false)

  @Test
  fun test() {
    val chain = listOf(
      object: AbstractHttpHandler<Acceptance, AbstractHttpHandler.Context>() {
        override fun context() = Context()
        suspend override fun acceptUri(method: Method,
                                       uri: String): Acceptance? {
          if (method == Method.GET || method == Method.HEAD) {
            return Acceptance(method, uri)
          }
          return null
        }
        suspend override fun handle(acceptance: Acceptance,
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
          socket.aWrite(body.flip())
        }
      }
    )

    Server(
      HttpHandlerChain(chain),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
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
          val bytes = it.entity.content.readAllBytes()
          assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.JSON))
          assertEquals(
            "{\"method\":\"GET\",\"path\":\"/\",\"headers\":{\"User-Agent\":\"Test user agent\",\"Cache-Control\":\"no-cache\",\"Pragma\":\"no-cache\",\"Test\":\"123\",\"Connection\":\"close\",\"Accept-Encoding\":\"gzip\",\"Host\":\"localhost:8080\"}}",
            String(bytes)
          )
        }
      }
    }
  }

}
