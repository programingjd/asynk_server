package info.jdavid.server

import info.jdavid.server.http.Headers
import info.jdavid.server.http.Method
import info.jdavid.server.http.SimpleHttpHandler
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

class HttpHandlerTests {

  @Test
  fun testDefaultHandlerNoBody() {
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
      conn.setRequestProperty("Test", "123")
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
            Test: 123
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

  @Test
  fun testDefaultHandlerWithMissingBody() {
    Server(
      SimpleHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use {
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.setRequestProperty(Headers.USER_AGENT, "Test user agent")
      conn.setRequestProperty(Headers.CACHE_CONTROL, "no-cache")
      conn.setRequestProperty(Headers.PRAGMA, "no-cache")
      conn.setRequestProperty(Headers.ACCEPT, "text/plain")
      conn.setRequestProperty(Headers.CONNECTION, "close")
      conn.setRequestProperty(Headers.CONTENT_TYPE, "text/plain")
      conn.useCaches = false
      conn.doOutput = false
      try {
        conn.inputStream.readBytes(512)
        fail("The request should have failed with a 400.")
      }
      catch (e: IOException) {
        assertEquals(400, conn.responseCode)
      }
      finally {
        conn.disconnect()
      }
    }
  }

  @Test
  fun testDefaultHandlerWithDisallowedBody() {
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
      conn.setRequestProperty(Headers.CONTENT_TYPE, "text/plain")
      conn.useCaches = false
      conn.doOutput = true
      conn.requestMethod = "HEAD"
      try {
        conn.outputStream.write("Test".toByteArray())
        val bytes = conn.inputStream.readBytes(512)
        fail("The request should have failed with a 400.")
      }
      catch (e: IOException) {
        assertEquals(400, conn.responseCode)
      }
      finally {
        conn.disconnect()
      }
    }
  }

  @Test
  fun testDefaultHandlerWithBody() {
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
      conn.setRequestProperty(Headers.CONTENT_TYPE, "text/plain")
      conn.useCaches = false
      conn.doOutput = true
      try {
        conn.outputStream.write("Test".toByteArray())
        val bytes = conn.inputStream.readBytes(512)
        assertEquals(
          """
            POST /

            User-Agent: Test user agent
            Cache-Control: no-cache
            Pragma: no-cache
            Accept: text/plain
            Connection: close
            Content-Type: text/plain
            Host: localhost:8080
            Content-Length: 4

            Test
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
