package info.jdavid.asynk.server

import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.SimpleHttpHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class DispatcherTests {

  @Test fun connectMany() {
    class ExtendedContext(others: Collection<*>?): AbstractHttpHandler.Context(others) {
      val test: ByteBuffer =
        (others?.find { it is ExtendedContext } as? ExtendedContext)?.test ?:
          "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nTest".
            toByteArray(Charsets.US_ASCII).let {
            val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
          }
    }
    val count = 10000
    val order = ConcurrentLinkedQueue<Int>()
    val times = Server(object : SimpleHttpHandler() {
      override suspend fun context(others: Collection<*>?) = ExtendedContext(others)
      override suspend fun connect(remoteAddress: InetSocketAddress) = true
      override suspend fun handle(acceptance: SimpleHttpHandler.Acceptance, headers: Headers, body: ByteBuffer,
                                  socket: AsynchronousSocketChannel, context: Context) {
        ((context as ExtendedContext).test.rewind() as ByteBuffer).apply {
          while (remaining() > 0) socket.asyncWrite(this)
        }
      }
    }).use { _ ->
      runBlocking {
        (1..count).map { i: Int ->
          async {
            val t = System.nanoTime()
            val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
            conn.useCaches = false
            try {
              val bytes = conn.inputStream.readBytes()
              if (String(bytes) == "Test") {
                order.add(i)
                println(i)
              }
            }
            catch (e: IOException) {
              e.printStackTrace()
            }
            finally {
              conn.disconnect()
            }
            System.nanoTime() - t
          }
        }.let { jobs ->
          jobs.map { it.await() }
        }
      }
    }
    val average = TimeUnit.NANOSECONDS.toMicros(
      times.map { it }.reduce { acc, l -> acc + l } / times.size
    )
    val largest = TimeUnit.NANOSECONDS.toMicros(
      times.map { it }.max() ?: throw RuntimeException()
    )
    assertEquals(count, order.size)
    assertTrue(average < 10000L) // 10 millis
    assertTrue(largest < 500000L) // 500 millis
    assertNotEquals(order, order.sorted())
  }

}
