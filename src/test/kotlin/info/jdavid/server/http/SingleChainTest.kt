package info.jdavid.server.http

import com.fasterxml.jackson.databind.ObjectMapper
import info.jdavid.server.Handler
import info.jdavid.server.Server
import kotlinx.coroutines.experimental.nio.aWrite
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class SingleChainTest {

  private class Acceptance(val method: Method, val uri: String): Handler.Acceptance {
    override val bodyAllowed: Boolean
      get() = false
    override val bodyRequired: Boolean
      get() = false
  }

  @Test
  fun test1() {
    val chain = listOf(
      object: AbstractHttpHandler<Acceptance>() {
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
                                    context: Any?) {
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
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      conn.setRequestProperty(Headers.USER_AGENT, "Test user agent")
      conn.setRequestProperty(Headers.CACHE_CONTROL, "no-cache")
      conn.setRequestProperty(Headers.PRAGMA, "no-cache")
      conn.setRequestProperty("Test", "123")
      conn.setRequestProperty(Headers.CONNECTION, "close")
      conn.useCaches = false
      try {
        val bytes = conn.inputStream.readBytes(512)
        assertEquals(conn.getHeaderField(Headers.CONTENT_LENGTH).toInt(), bytes.size)
        assertTrue(conn.getHeaderField(Headers.CONTENT_TYPE).startsWith("application/json"))
        assertEquals(
          "{\"method\":\"GET\",\"path\":\"/\",\"headers\":{\"User-Agent\":\"Test user agent\",\"Cache-Control\":\"no-cache\",\"Pragma\":\"no-cache\",\"Test\":\"123\",\"Connection\":\"close\",\"Host\":\"localhost:8080\",\"Accept\":\"text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\"}}",
          String(bytes)
        )
      }
      finally {
        conn.disconnect()
      }
    }
  }

}
