package info.jdavid.server.dev

import info.jdavid.server.Server
import info.jdavid.server.http.Headers
import info.jdavid.server.http.AbstractHttpHandler
import info.jdavid.server.http.SimpleHttpHandler
import info.jdavid.server.http.Uri
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
  connectFor(60000L)
//  connectMany()
}

fun connectFor(millis: Long) {
  Server(object : SimpleHttpHandler() {
    suspend override fun connect(remoteAddress: InetSocketAddress) = true
    suspend override fun handle(acceptance: SimpleHttpHandler.Acceptance, headers: Headers, body: ByteBuffer,
                                socket: AsynchronousSocketChannel, context: Context) {
      when (Uri.path(acceptance.uri)) {
        "/headers", "/headers/" -> {
          val bytes =
            headers.lines.joinToString("\n", "", "\n").toByteArray(Charsets.US_ASCII)
          val size = bytes.size
          val type = "text/plain"
          val setup =
            "HTTP/1.1 200 OK\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate(bytes.size + setup.size).apply {
            put(setup)
            put(bytes)
            rewind()
          })
        }
        "/body", "/body/" -> {
          val size = body.remaining()
          val type = headers.value("Content-Type") ?: "text/plain"
          val setup =
            "HTTP/1.1 200 OK\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate((body.remaining()) + setup.size).apply {
            put(setup)
            if (body.remaining() > 0) put(body)
            rewind()
          })
        }
        else -> {
          val bytes = "NOT FOUND\n\nAvailable endpoints:\n/body\n/headers\n\n".toByteArray(Charsets.US_ASCII)
          val size = bytes.size
          val type = "text/plain"
          val setup =
            "HTTP/1.1 404 NOT FOUND\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate(bytes.size + setup.size).apply {
            put(setup)
            put(bytes)
            rewind()
          })
        }
      }

    }
  },/* SimpleHttpHandler(),*/ InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096).use {
    Thread.sleep(millis)
  }
}

fun connectMany() {
  class ExtendedContext: AbstractHttpHandler.Context() {
    val test =
      "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nTest".
      toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
  }
  Server(object : SimpleHttpHandler() {
    override fun context() = ExtendedContext()
    suspend override fun connect(remoteAddress: InetSocketAddress) = true
    suspend override fun handle(acceptance: SimpleHttpHandler.Acceptance, headers: Headers, body: ByteBuffer,
                                socket: AsynchronousSocketChannel, context: Context) {
      socket.aWrite((context as ExtendedContext).test.rewind() as ByteBuffer)
    }
  }).use {
    runBlocking {
      (0..10000).map { i: Int ->
        async {
          val t = System.nanoTime()
          val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
          conn.useCaches = false
          try {
            val bytes = conn.inputStream.readBytes(512)
            if (String(bytes) == "Test") println(i)
          }
          catch (e: IOException) {
            e.printStackTrace()
          }
          finally {
            conn.disconnect()
          }
          System.nanoTime() - t
        }
      }.apply {
        println()
        println(TimeUnit.NANOSECONDS.toMicros(map { it.await() }.reduce { acc, l -> acc + l } / size))
      }
    }
  }
}
