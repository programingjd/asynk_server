package info.jdavid.asynk.server

import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.toList
import kotlinx.coroutines.experimental.currentScope
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.coroutineContext

class EchoTests {

  @Test
  fun echo() {
    Server(object: Handler<Unit> {
      override suspend fun context(others: Collection<*>?) = Unit
      override suspend fun connect(remoteAddress: InetSocketAddress) = true
      override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Unit) {
        while (socket.aRead(buffer, 5, TimeUnit.SECONDS) > -1) {
          (buffer.flip() as ByteBuffer).apply {
            while (remaining() > 0) socket.aWrite(this, 5, TimeUnit.SECONDS)
          }
          buffer.flip()
        }
      }
    }).use {
      //Thread.sleep(20000L)
      runBlocking {
        awaitAll(
          async {
            AsynchronousSocketChannel.open().use {
              it.setOption(StandardSocketOptions.TCP_NODELAY, true)
              it.setOption(StandardSocketOptions.SO_REUSEADDR, true)
              it.aConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080))
              ByteBuffer.wrap("abc\r\ndef\r\n".toByteArray()).apply {
                while (remaining() > 0) it.aWrite(this)
              }
              delay(100)
              ByteBuffer.wrap("ghi\r\njkl\r\nmno".toByteArray()).apply {
                while (remaining() > 0) it.aWrite(this)
              }
              it.shutdownOutput()
              val buffer = ByteBuffer.allocate(128)
              assertEquals(23, aRead(it, buffer))
              assertEquals("abc\r\ndef\r\nghi\r\njkl\r\nmno",
                           String(ByteArray(23).apply { (buffer.flip() as ByteBuffer).get(this) }))
            }
          },
          async {
            AsynchronousSocketChannel.open().use {
              it.setOption(StandardSocketOptions.TCP_NODELAY, true)
              it.setOption(StandardSocketOptions.SO_REUSEADDR, true)
              it.aConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080))
              ByteBuffer.wrap("123\r\n".toByteArray()).apply {
                while (remaining() > 0) it.aWrite(this)
              }
              delay(50)
              ByteBuffer.wrap("456\r\n789".toByteArray()).apply {
                while (remaining() > 0) it.aWrite(this)
              }
              it.shutdownOutput()
              val buffer = ByteBuffer.allocate(128)
              assertEquals(13, aRead(it, buffer))
              assertEquals("123\r\n456\r\n789",
                           String(ByteArray(13).apply { (buffer.flip() as ByteBuffer).get(this) }))
            }
          }
        )
      }
    }
  }

  private suspend fun aRead(socket: AsynchronousSocketChannel, buffer: ByteBuffer) =
    CoroutineScope(coroutineContext).produce {
        while (true) {
          val n = socket.aRead(buffer)
          if (n > 0) send(n) else break
        }
        close()
      }.toList().sum()

}
