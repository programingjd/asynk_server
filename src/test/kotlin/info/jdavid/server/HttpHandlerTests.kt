package info.jdavid.server

import info.jdavid.server.http.Headers
import info.jdavid.server.http.SimpleHttpHandler
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

class HttpHandlerTests {

  @Test
  fun testDefaultHandler() {
    Server(
      SimpleHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use {
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      conn.setRequestProperty(Headers.USER_AGENT, "Test user agent")
      conn.setRequestProperty(Headers.CACHE_CONTROL, "no-cache")
      conn.setRequestProperty(Headers.PRAGMA, "no-cache")
      conn.setRequestProperty(Headers.ACCEPT, "text/plain")
      conn.setRequestProperty(Headers.CONNECTION, "close")
      conn.useCaches = false
      try {
        val bytes = conn.inputStream.readBytes(512)
        assertEquals(
          """
            GET /

            User-Agent: Test user agent
            Cache-Control: no-cache
            Pragma: no-cache
            Accept: text/plain
            Connection: close
            Host: localhost:8080


          """.trimIndent().normalize()
          , String(bytes).normalize())
      }
      finally {
        conn.disconnect()
      }
    }
  }

  private fun String.normalize(): String {
    return replace("\r","").replace("\n","\r\n")
  }

}
