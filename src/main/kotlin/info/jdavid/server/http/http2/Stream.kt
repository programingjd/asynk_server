package info.jdavid.server.http.http2

import info.jdavid.server.http.http11.Http11Connection
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import java.nio.ByteBuffer

internal class Stream(val id: Int,
                      buffers: LockFreeLinkedListHead, maxRequestSize: Int): LockFreeLinkedListNode() {
  private val node = buffers.removeFirstOrNull() as? Node ?: Node(8192, maxRequestSize)

  var state = State.IDLE

  internal fun recycle() {

  }

  enum class State {
    IDLE, OPEN, RESERVED, HALF_CLOSED, CLOSED
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
