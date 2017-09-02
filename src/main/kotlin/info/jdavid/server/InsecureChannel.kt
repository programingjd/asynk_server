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
  private val segmentR = node.segmentR
  private val segmentW = node.segmentW
  private val buffer = node.buffer
  private var exhausted = false

  override fun next() {
    segmentW.rewind().limit(segmentW.capacity())
    segmentR.rewind().limit(segmentR.capacity())
    buffer.rewind().limit(buffer.capacity())
  }

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {}

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {}

  override fun recycle() {
    nodes.addLast(node)
  }

  override fun buffer() = buffer

  override fun segmentW() = segmentW

  override fun segmentR() = segmentR

  suspend override fun read(deadline: Long) = read(deadline, 8192)

  suspend override fun read(deadline: Long, bytes: Int): ByteBuffer {
    segmentR.rewind().limit(segmentR.capacity())
    if (exhausted) return segmentR.limit(0) as ByteBuffer
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    val n = channel.aRead(segmentR, timeout, TimeUnit.NANOSECONDS)
    if (n == -1) {
      exhausted = true
    }
    return segmentR.flip() as ByteBuffer
  }

  suspend override fun write(deadline: Long, byteBuffer: ByteBuffer) {
    byteBuffer.flip()
    val timeout = deadline - System.nanoTime()
    if (timeout < 0L) throw InterruptedByTimeoutException()
    channel.aWrite(byteBuffer, timeout, TimeUnit.NANOSECONDS)
    byteBuffer.rewind().limit(byteBuffer.capacity())
  }

  private class Node(segmentSize: Int, bufferSize: Int): LockFreeLinkedListNode() {
    internal val segmentR = ByteBuffer.allocateDirect(segmentSize)
    internal val segmentW = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
