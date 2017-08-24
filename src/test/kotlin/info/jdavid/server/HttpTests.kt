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

  @Test fun testReject() {
    val server = Config().
      port(8080).
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
      URL("http://localhost:8080").readBytes()
      fail()
    }
    catch (ignore: SocketException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyOK() {
    val server = Config().
      port(8080).
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
      val bytes = URL("http://localhost:8080").readBytes()
      assertEquals(0, bytes.size)
    }
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyNotFound() {
    val server = Config().
      port(8080).
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
      URL("http://localhost:8080").readBytes()
      fail()
    }
    catch (ignore: FileNotFoundException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testEmptyError() {
    val server = Config().
      port(8080).
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
      URL("http://localhost:8080").readBytes()
      fail()
    }
    catch (ignore: IOException) {}
    finally {
      server.stop()
    }
  }

  @Test fun testSimpleOK() {
    val server = Config().
      port(8080).
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
      assertEquals("abcd", URL("http://localhost:8080").readText())
    }
    finally {
      server.stop()
    }
  }

  @Test fun testHeaders() {
    val server = Config().
      port(8080).
      requestHandler(object: HttpRequestHandler() {
        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
          buffer.rewind().limit(buffer.capacity())
          buffer.put(
            "HTTP/1.1 200 OK\r\nContent-Length: 4\r\na: b\r\nCache-Control: no-store\n\r\nabcd".
              toByteArray(HttpRequestHandler.ASCII)
          )
          channel.write(buffer, deadline)
        }
        suspend override fun reject(address: InetSocketAddress) = false
      }).
      startServer()
    try {
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      try {
        conn.connect()
        assertEquals(200, conn.responseCode)
        assertEquals("OK", conn.responseMessage)
        val bytes = conn.inputStream.readAllBytes()
        assertEquals(4, bytes.size)
        assertEquals("abcd", String(bytes))
        assertEquals("4", conn.getHeaderField("Content-Length"))
        assertEquals("nocache", conn.getHeaderField("Cache-Control"))
        assertEquals("b", conn.getHeaderField("a"))
      }
      finally {
        conn.disconnect()
      }
      assertEquals("abcd", URL("http://localhost:8080").readText())
    }
    finally {
      server.stop()
    }
  }


//  @Test fun testOK() {
//    val server = Config().
//      port(8080).
//      requestHandler(object: HttpRequestHandler() {
//        suspend override fun handle(address: InetSocketAddress, method: String, uri: String, headers: Headers,
//                                    channel: Channel, deadline: Long, buffer: ByteBuffer) {
//        }
//        suspend override fun reject(address: InetSocketAddress) = false
//      }).
//      startServer()
//    buffer.rewind().limit(buffer.capacity())
//    buffer.put("HTTP/1.1 200 OK\r\n".toByteArray(HttpRequestHandler.ASCII))
//    channel.write(buffer, deadline)
//    val h = Headers()
//    h.add(Headers.CONTENT_TYPE, "text/plain; charset=utf-8")
//    h.add(Headers.CONTENT_LENGTH, "6")
//    channel.write(h, deadline)
//    buffer.put("body\n".toByteArray(HttpRequestHandler.UTF_8))
//    channel.write(buffer, deadline)
//    delay(5, TimeUnit.SECONDS)
//    buffer.put("\n".toByteArray(HttpRequestHandler.UTF_8))
//    channel.write(buffer, deadline)
//
//
//  }


}
