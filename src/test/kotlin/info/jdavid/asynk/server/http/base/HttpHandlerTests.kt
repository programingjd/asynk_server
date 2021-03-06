package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.http.Crypto
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.server.Server
import org.apache.http.client.methods.*
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.URI
import java.security.SecureRandom

class HttpHandlerTests {

  @Test fun testDefaultHandlerNoBody() {
    Server(
      DefaultHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use { _ ->
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.ACCEPT, "text/plain")
        setHeader(Headers.ACCEPT_ENCODING, "identity")
        setHeader("Test", "123")
        setHeader(Headers.CONNECTION, "close")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
          assertEquals(
            """
            GET /

            User-Agent: Test user agent
            Cache-Control: no-cache
            Pragma: no-cache
            Accept: text/plain
            Accept-Encoding: identity
            Test: 123
            Connection: close
            Host: localhost:8080


          """.trimIndent().normalize()
            , String(bytes).normalize())
        }
      }
    }
  }

  @Test fun testDefaultHandlerWithMissingBody() {
    Server(
      DefaultHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use { _ ->
      val request = HttpPost().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.ACCEPT, "text/plain")
        setHeader(Headers.ACCEPT_ENCODING, "identity")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.CONTENT_TYPE, "text/plain")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(400, it.statusLine.statusCode)
        }
      }
    }
  }

  @Test fun testDefaultHandlerWithDisallowedBody() {
    Server(
      DefaultHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use { _ ->
      val request = object: HttpEntityEnclosingRequestBase() {
        override fun getMethod() = "HEAD"
      }.apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.ACCEPT, "text/plain")
        setHeader(Headers.ACCEPT_ENCODING, "identity")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.CONTENT_TYPE, "text/plain")
        entity = StringEntity("Test")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(400, it.statusLine.statusCode)
        }
      }
    }
  }

  @Test fun testDefaultHandlerWithBody() {
    Server(
      DefaultHttpHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use { _ ->
      val request = HttpPut().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.ACCEPT, "text/plain")
        setHeader(Headers.ACCEPT_ENCODING, "identity")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.CONTENT_TYPE, "text/plain")
        entity = StringEntity("Test")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
          assertEquals(
            """
            PUT /

            User-Agent: Test user agent
            Cache-Control: no-cache
            Pragma: no-cache
            Accept: text/plain
            Accept-Encoding: identity
            Connection: close
            Content-Type: text/plain
            Content-Length: 4
            Host: localhost:8080

            Test
          """.trimIndent().normalize()
            , String(bytes).normalize())
        }
      }
    }
  }

  @Test fun testDefaultHandlerWithLargeBody() {
    val data = SecureRandom.getSeed(7500)
    Server(
      DefaultHttpHandler(16384),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
    ).use { _ ->
      val request = HttpPut().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.ACCEPT, "text/plain")
        setHeader(Headers.ACCEPT_ENCODING, "identity")
        setHeader(Headers.CONNECTION, "close")
        setHeader(Headers.CONTENT_TYPE, "application/octet-stream")
        entity = ByteArrayEntity(data)
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(200, it.statusLine.statusCode)
          val bytes = it.entity.content.readBytes()
          assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
          assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
          assertEquals(
            """
            PUT /

            User-Agent: Test user agent
            Cache-Control: no-cache
            Pragma: no-cache
            Accept: text/plain
            Accept-Encoding: identity
            Connection: close
            Content-Type: application/octet-stream
            Content-Length: ${data.size}
            Host: localhost:8080

            ${Crypto.hex(data)}
          """.trimIndent().normalize()
            , String(bytes).normalize())
        }
      }
    }
  }

  private fun String.normalize(): String {
    return replace("\r","").replace("\n","\r\n")
  }

}
