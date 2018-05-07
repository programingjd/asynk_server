package info.jdavid.server.http.handler

import info.jdavid.server.Server
import info.jdavid.server.http.Headers
import info.jdavid.server.http.MediaType
import info.jdavid.server.http.Method
import info.jdavid.server.http.Uri
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert
import org.junit.Test
import org.junit.Assert.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

class HandlerChainTests {

  @Test fun testSimple() {
    val range = (1..5)
    val handlers = range.map {
      HttpHandler.of(
        NumberedRoute(it),
        { _ , _, _, _ ->
          HttpHandler.StringResponse(it.toString(), MediaType.TEXT)
        }
      )
    }
    Server(
      HttpHandlerChain(handlers),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use {
      range.forEach { n ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${n}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use {
          it.execute(request).use {
            Assert.assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            Assert.assertEquals(it.getLastHeader(
              Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
            Assert.assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(
              MediaType.TEXT))
            assertEquals(n.toString(), String(bytes))
          }
        }
      }
    }

  }

  private class NumberedRoute(val number: Int): HttpHandler.Route<Unit> {
    override fun match(method: Method, uri: String): Unit? {
      return if (Uri.lastPathSegment(uri) == number.toString()) Unit else null
    }
  }

}
