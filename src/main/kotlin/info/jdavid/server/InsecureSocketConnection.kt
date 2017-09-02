package info.jdavid.server

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit

internal class InsecureSocketConnection(private val channel: AsynchronousSocketChannel,
                                        private val segmentPool: LockFreeLinkedListHead): SocketConnection() {
  private val segments = segments(segmentPool)
  private val segmentRead = segments.segmentR
  private val segmentWrite = segments.segmentW
  private val segment = segments.segment
  private var exhausted = false

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {}

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {}

  override fun recycleBuffers() {
    segmentPool.addLast(segments)
  }

  override fun segment(): ByteBuffer = segment

  override fun segmentW(): ByteBuffer = segmentWrite

  override fun segmentR(): ByteBuffer = segmentRead

  suspend override fun read(deadline: Long) = read(deadline, 16384)

  suspend override fun read(deadline: Long, bytes: Int): ByteBuffer {
    segmentRead.rewind().limit(segmentRead.capacity())
    if (bytes > segmentRead.capacity()) throw IllegalArgumentException("Too many bytes requested.")
    if (exhausted) return segmentRead.limit(segmentRead.position()) as ByteBuffer
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    val n = channel.aRead(segmentRead, timeout, TimeUnit.NANOSECONDS)
    if (n == -1) {
      exhausted = true
    }
    return segmentRead.flip() as ByteBuffer
  }

  suspend override fun write(deadline: Long, byteBuffer: ByteBuffer) {
    byteBuffer.flip()
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    channel.aWrite(byteBuffer, timeout, TimeUnit.NANOSECONDS)
    byteBuffer.rewind().limit(byteBuffer.capacity())
  }

  private companion object {

    fun segments(segmentPool: LockFreeLinkedListHead): Segments {
      return segmentPool.removeFirstOrNull() as? Segments ?: Segments(16384)
    }

  }

  private class Segments(segmentSize: Int): LockFreeLinkedListNode() {
    internal val segmentR = ByteBuffer.allocateDirect(segmentSize)
    internal val segmentW = ByteBuffer.allocateDirect(segmentSize)
    internal val segment = ByteBuffer.allocateDirect(segmentSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
