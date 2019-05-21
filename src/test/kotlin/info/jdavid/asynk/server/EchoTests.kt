package info.jdavid.asynk.server

import info.jdavid.asynk.core.asyncConnect
import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import kotlin.coroutines.coroutineContext

class EchoTests {

  @Test
  fun echo() {
    Server(object: Handler<Unit> {
      override suspend fun context(others: Collection<*>?) = Unit
      override suspend fun connect(remoteAddress: InetSocketAddress) = true
      override suspend fun handle(socket: AsynchronousSocketChannel, remoteAddress: InetSocketAddress,
                                  buffer: ByteBuffer, context: Unit) {
        while (withTimeout(5000L) { socket.asyncRead(buffer) } > -1) {
          (buffer.flip() as ByteBuffer).apply {
            while (remaining() > 0) this.also { withTimeout(5000L) { socket.asyncWrite(it) } }
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
              it.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080))
              ByteBuffer.wrap("abc\r\ndef\r\n".toByteArray()).apply {
                while (remaining() > 0) it.asyncWrite(this)
              }
              delay(100)
              ByteBuffer.wrap("ghi\r\njkl\r\nmno".toByteArray()).apply {
                while (remaining() > 0) it.asyncWrite(this)
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
              it.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080))
              ByteBuffer.wrap("123\r\n".toByteArray()).apply {
                while (remaining() > 0) it.asyncWrite(this)
              }
              delay(50)
              ByteBuffer.wrap("456\r\n789".toByteArray()).apply {
                while (remaining() > 0) it.asyncWrite(this)
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

  @Suppress("EXPERIMENTAL_API_USAGE")
  private suspend fun aRead(socket: AsynchronousSocketChannel, buffer: ByteBuffer) =
    CoroutineScope(coroutineContext).produce {
        while (true) {
          val n = socket.asyncRead(buffer)
          if (n > 0) send(n) else break
        }
        close()
      }.toList().sum()

}
