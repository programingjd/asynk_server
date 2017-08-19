package info.jdavid.server

import java.nio.ByteBuffer

abstract class Channel internal constructor() {

  abstract suspend fun read( deadline: Long): ByteBuffer

  abstract suspend fun read(bytes: Int, deadline: Long): ByteBuffer

  abstract suspend fun write(byteBuffer: ByteBuffer, deadline: Long)

  abstract internal fun next()

  abstract suspend internal fun start(readDeadline: Long, writeDeadline: Long)

  abstract suspend internal fun stop(readDeadline: Long, writeDeadline: Long)

  abstract internal fun recycle()

  abstract internal fun buffer(): ByteBuffer

  abstract internal fun segment(): ByteBuffer

  suspend fun write(bytes: ByteArray, deadline: Long) {
    val segment = segment()
    segment.rewind().limit(segment.capacity())
    val n = bytes.size
    if (n > segment.remaining()) {
      var offset = 0
      while (true) {
        val length = segment.remaining()
        if ((length + offset) >= n) {
          segment.put(bytes, offset, n - offset)
          write(segment, deadline)
          break
        }
        else {
          segment.put(bytes, offset, length)
          write(segment, deadline)
          offset += length
        }
      }
    }
    else {
      segment.put(bytes)
      write(segment, deadline)
    }
  }

  suspend fun write(headers: Headers, deadline: Long) {
    val segment = segment()
    segment.rewind().limit(segment.capacity())
    headers.lines.forEach {
      val bytes = it.toByteArray(ISO_8859_1)
      val n = bytes.size
      if (n > segment.remaining()) {
        var offset = 0
        while (true) {
          val length = segment.remaining()
          if ((length + offset) >= n) {
            segment.put(bytes, offset, n - offset)
            if ((length + offset - 2) >= n) {
              segment.put(CRLF)
              write(segment, deadline)
            }
            else {
              write(segment, deadline)
              segment.put(CRLF)
              write(segment, deadline)
            }
            break
          }
          else {
            segment.put(bytes, offset, length)
            write(segment, deadline)
            offset += length
          }
        }
      }
      else {
        segment.put(bytes)
        if (segment.remaining() > 2) {
          segment.put(CRLF)
          write(segment, deadline)
        }
        else {
          write(segment, deadline)
          segment.put(CRLF)
          write(segment, deadline)
        }
      }
    }
    segment.put(CRLF)
    write(segment, deadline)
  }

  private companion object {
    val ISO_8859_1 = Charsets.ISO_8859_1
    val CRLF: ByteArray = byteArrayOf(0x0d, 0x0a)
  }

}
