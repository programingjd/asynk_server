package info.jdavid.server.discarded

import info.jdavid.server.discarded.http.http11.Headers
import java.nio.ByteBuffer

abstract class SocketConnection internal constructor() {

  abstract suspend fun read(deadline: Long): ByteBuffer

  abstract suspend fun read(deadline: Long, bytes: Int): ByteBuffer

  abstract suspend fun write(deadline: Long, byteBuffer: ByteBuffer)

  abstract suspend internal fun start(readDeadline: Long, writeDeadline: Long)

  abstract suspend internal fun stop(readDeadline: Long, writeDeadline: Long)

  abstract internal fun recycleBuffers()

  abstract internal fun segment(): ByteBuffer

  abstract protected fun segmentW(): ByteBuffer

  abstract protected fun segmentR(): ByteBuffer

  suspend fun write(deadline: Long, vararg arrays: ByteArray) {
    val segment = segmentW()
    segment.rewind().limit(segment.capacity())
    for (bytes in arrays) {
      val n = bytes.size
      if (n > segment.remaining()) {
        var offset = 0
        while (true) {
          val length = segment.remaining()
          if ((length + offset) >= n) {
            segment.put(bytes, offset, n - offset)
            break
          }
          else {
            segment.put(bytes, offset, length)
            write(deadline, segment)
            offset += length
          }
        }
      }
      else {
        segment.put(bytes)
      }
    }
    write(deadline, segment)
  }

  suspend fun write(deadline: Long, headers: Headers) {
    val lines = headers.lines
    val max = lines.size * 2
    val array = Array(max + 1, {
      if (it == max || it % 2 != 0) CRLF else lines[it / 2].toByteArray(ISO_8859_1)
    })
    write(deadline, *array)
  }

  private companion object {
    val ISO_8859_1 = Charsets.ISO_8859_1
    val CRLF: ByteArray = byteArrayOf(0x0d, 0x0a)
  }

}
