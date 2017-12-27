package info.jdavid.server.dev

import kotlinx.coroutines.experimental.nio.aWrite
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
  connectFor(60000L)
}

fun connectFor(millis: Long) {
  val server = Server(HttpHandler())
  Thread.sleep(millis)
  server.stop()
}

fun connectMany() {
  class ExtendedContext: HttpHandler.Context() {
    val test =
      "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nTest".
      toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
  }
  val server = Server(object: HttpHandler() {
    suspend override fun context() = ExtendedContext()
    suspend override fun handle(method: Method, path: String, headers: Headers, body: ByteBuffer?,
                                socket: AsynchronousSocketChannel, context: Any?) {
      socket.aWrite((context as ExtendedContext).test.rewind() as ByteBuffer)
    }
  })
  val executors = Executors.newFixedThreadPool(32)
  for (i in 0..1000) {
    executors.submit {
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      conn.useCaches = false
      try {
        val bytes = conn.inputStream.readBytes(512)
        if (String(bytes) == "Test") println(i)
      }
      finally {
        conn.disconnect()
      }
    }
  }
  executors.shutdown()
  executors.awaitTermination(15000L, TimeUnit.MILLISECONDS)
  server.stop()
}
