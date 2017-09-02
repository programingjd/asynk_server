package info.jdavid.server.http.http11

import info.jdavid.server.Connection
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import java.nio.ByteBuffer

class Http11Connection(buffers: LockFreeLinkedListHead, maxRequestSize: Int): Connection(buffers) {
  private val node = buffers.removeFirstOrNull() as? Node ?: Node(8192, maxRequestSize)

  internal val segmentR = node.segmentR
  internal val segmentW = node.segmentW
  internal val buffer = node.buffer

  override fun next() {
    segmentW.rewind().limit(segmentW.capacity())
    segmentR.rewind().limit(segmentR.capacity())
    buffer.rewind().limit(buffer.capacity())
  }

  override fun recycle() {
    buffers.addLast(node)
  }

  suspend override fun close() {}

  private class Node(segmentSize: Int, bufferSize: Int): LockFreeLinkedListNode() {
    internal val segmentR = ByteBuffer.allocateDirect(segmentSize)
    internal val segmentW = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
