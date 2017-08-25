package info.jdavid.server

import org.junit.Test
import org.junit.Assert.*
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.URL
import java.nio.ByteBuffer

class HttpTests {

  private val port = 8085

  @Test fun testRejectClientAddress() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = true
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      fail()
    }
    catch (ignore: SocketException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testRejectMethod() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        override fun acceptUri(method: String, uri: String) = 404
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      fail()
    }
    catch (ignore: FileNotFoundException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testRejectHeaders() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        override fun acceptHeaders(method: String, uri: String, headers: Headers) = 403
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nCache-Control: no-store\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      fail()
    }
    catch (ignore: IOException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testRejectBody() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        override fun acceptBody(method: String) = 400
        override fun acceptUri(method: String, uri: String) = -1
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nCache-Control: no-store\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      val conn = URL("http://localhost:${port}").openConnection() as HttpURLConnection
      try {
        val post = "abcd".toByteArray()
        conn.requestMethod = "POST"
        conn.addRequestProperty("Content-Length", post.size.toString())
        conn.doOutput = true
        conn.outputStream.apply { write(post) }.close()
        assertEquals(400, conn.responseCode)
        assertEquals("Bad Request", conn.responseMessage)
      }
      finally {
        conn.disconnect()
      }
    }
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyOK() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nCache-Control: no-store\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      val bytes = URL("http://localhost:${port}").readBytes()
      assertEquals(0, bytes.size)
    }
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyNotFound() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      fail()
    }
    catch (ignore: FileNotFoundException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyError() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      URL("http://localhost:${port}").readBytes()
      fail()
    }
    catch (ignore: IOException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testSimpleOK() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nCache-Control: no-store\r\n\r\nabcd".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      assertEquals("abcd", URL("http://localhost:${port}").readText())
    }
    finally {
      server.stop()
    }
  }

  @Test fun testHeaders() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 4\r\na: b\r\nCache-Control: no-store\r\n\r\nabcd".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      val conn = URL("http://localhost:${port}").openConnection() as HttpURLConnection
      try {
        assertEquals(200, conn.responseCode)
        assertEquals("OK", conn.responseMessage)
        val bytes = conn.inputStream.readAllBytes()
        assertEquals(4, bytes.size)
        assertEquals("abcd", String(bytes))
        assertEquals("4", conn.getHeaderField("Content-Length"))
        assertEquals("no-store", conn.getHeaderField("Cache-Control"))
        assertEquals("b", conn.getHeaderField("a"))
      }
      finally {
        conn.disconnect()
      }
      assertEquals("abcd", URL("http://localhost:${port}").readText())
    }
    finally {
      server.stop()
    }
  }

  @Test fun testPost() {
    val server = Config().
      port(port).
      requestHandler(object: HttpRequestHandler() {
        override fun acceptUri(method: String, uri: String) = -1
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          val array = ByteArray(buffer.remaining())
          buffer.get(array)
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: ${array.size}\r\nCache-Control: no-store\r\n\r\n".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
          channel.write(array, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      val conn = URL("http://localhost:${port}").openConnection() as HttpURLConnection
      try {
        val post = "abcd".toByteArray()
        conn.requestMethod = "POST"
        conn.addRequestProperty("Content-Length", post.size.toString())
        conn.doOutput = true
        conn.outputStream.apply { write(post) }.close()
        assertEquals(200, conn.responseCode)
        assertEquals("OK", conn.responseMessage)
        val bytes = conn.inputStream.readAllBytes()
        assertEquals(4, bytes.size)
        assertEquals("abcd", String(bytes))
        assertEquals("4", conn.getHeaderField("Content-Length"))
        assertEquals("no-store", conn.getHeaderField("Cache-Control"))
      }
      finally {
        conn.disconnect()
      }
    }
    finally {
      server.stop()
    }
  }

}
