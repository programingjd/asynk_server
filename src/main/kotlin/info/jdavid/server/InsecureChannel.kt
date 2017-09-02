package info.jdavid.server

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit

internal class InsecureChannel(private val channel: AsynchronousSocketChannel): Channel() {
  private var exhausted = false

  override fun recycle() {}

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {}

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {}

  suspend override fun read(deadline: Long, bytes: Int, segment: ByteBuffer): ByteBuffer {
    segment.rewind().limit(segment.capacity())
    if (exhausted) return segment.limit(0) as ByteBuffer
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    val n = channel.aRead(segment, timeout, TimeUnit.NANOSECONDS)
    if (n == -1) {
      exhausted = true
    }
    return segment.flip() as ByteBuffer
  }

  suspend override fun write(deadline: Long, byteBuffer: ByteBuffer) {
    byteBuffer.flip()
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    channel.aWrite(byteBuffer, timeout, TimeUnit.NANOSECONDS)
    byteBuffer.rewind().limit(byteBuffer.capacity())
  }

}
