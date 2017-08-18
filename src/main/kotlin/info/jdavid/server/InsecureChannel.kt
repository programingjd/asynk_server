package info.jdavid.server

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit

internal class InsecureChannel(private val channel: AsynchronousSocketChannel,
                               private val nodes: LockFreeLinkedListHead,
                               maxRequestSize: Int): Channel() {
  private val node = nodes.removeFirstOrNull() as? Node ?: Node(8192, maxRequestSize)
  private val segment = node.segment
  private val buffer = node.buffer
  private var exhausted = false

  override fun next() {
    segment.rewind().limit(segment.capacity())
    buffer.rewind().limit(buffer.capacity())
  }

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {}

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {}

  override fun recycle() {
    nodes.addLast(node)
  }

  override fun buffer() = buffer

  override fun segment() = segment

  suspend override fun read(deadline: Long) = read(8192, deadline)

  suspend override fun read(bytes: Int, deadline: Long): ByteBuffer {
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

  suspend override fun write(byteBuffer: ByteBuffer, deadline: Long) {
    byteBuffer.flip()
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    channel.aWrite(byteBuffer, timeout, TimeUnit.NANOSECONDS)
    byteBuffer.rewind().limit(byteBuffer.capacity())
  }

  private class Node(segmentSize: Int, bufferSize: Int): LockFreeLinkedListNode() {
    internal val segment = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
