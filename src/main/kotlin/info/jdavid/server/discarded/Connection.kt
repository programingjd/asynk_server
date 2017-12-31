package info.jdavid.server.discarded

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import java.nio.ByteBuffer

abstract class Connection(private val bufferPool: LockFreeLinkedListHead,
                          private val maxRequestSize: Int) {

  suspend open fun close() {}

  internal fun buffers(): Buffers {
    return bufferPool.removeFirstOrNull() as? Buffers ?: Buffers(maxRequestSize)
  }

  internal fun recycle(buffers: Buffers) {
    buffers.buffer.rewind().limit(buffers.buffer.capacity())
    bufferPool.addLast(buffers)
  }

  internal class Buffers(bufferSize: Int): LockFreeLinkedListNode() {
    val buffer = ByteBuffer.allocateDirect(bufferSize)
  }

}
