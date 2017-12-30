package info.jdavid.server.dev

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

object Http {

  internal fun method(buffer: ByteBuffer): Method? {
    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // Look for first space -> METHOD

    // Shortest possible request line is 16 bytes long
    if (buffer.remaining() < 16) return null

    // 1. Look for first space to extract METHOD
    var i = 0
    while (true) {
      if (i == 7) return null
      val b = buffer[i++]
      if (validMethod(b)) continue
      if (b == SPACE) break
      return null
    }
    val methodBytes = ByteArray(i - 1)
    buffer.get(methodBytes)
    buffer.get()
    return Method.from(String(methodBytes, Charsets.US_ASCII))
  }

  internal fun path(buffer: ByteBuffer): String? {
    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // 1. look for first space -> METHOD (already done)
    // 2. look for second space -> URI (should start with a slash)
    // 3. check that rest of line is correct.
    //
    // URI can be:
    // * (usually used with OPTIONS to allow server-wide CORS) -> not supported.
    // an URL (for calls to a proxy)
    // an absolute path

    var i = buffer.position()
    val j = i
    while (true) {
      if (i == buffer.remaining()) return null
      val b = buffer[i++]
      if (validUrl(b)) continue
      if (b == SPACE) break
      return null
    }
    val uriBytes = ByteArray(i - j - 1)
    buffer.get(uriBytes)
    val uri = String(uriBytes)
    buffer.get()

    // 3. HTTP/1.1\r\n should follow
    if (buffer.get() != H_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != T_UPPER ||
        buffer.get() != P_UPPER ||
        buffer.get() != SLASH ||
        buffer.get() != ONE ||
        buffer.get() != DOT ||
        buffer.get() != ONE ||
        buffer.get() != CR ||
        buffer.get() != LF) return null

    if (uri[0] == '/') return uri // absolute path

    // url
    if (uri[0] != 'h' || uri[1] != 't' || uri[2] != 't' || uri[3] != 'p') return null
    if (uri[5] == ':') {
      if (uri[6] != '/' || uri[7] != '/') return null
      return uri.substring(7)
    }
    else if (uri[5] != 's' || uri[6] != ':') return null
    if (uri[7] != '/' || uri[7] != '/') return null
    return uri.substring(8)
  }

  internal suspend fun headers(socket: AsynchronousSocketChannel,
                               alreadyExhausted: Boolean,
                               buffer: ByteBuffer,
                               headers: Headers,
                               maxSize: Int = 8192): Boolean? {
    // Headers
    // FIELD_NAME_1: FIELD_VALUE_1\r\n
    // ...
    // FIELD_NAME_N: FIELD_VALUE_N\r\n
    // \r\n
    // Add content between \r\n as header lines until an empty line signifying the end of the headers.

    var exhausted = alreadyExhausted
    var i = buffer.position()
    var size = 0
    var j = i
    while (true) {
      if (++size > maxSize) throw HeadersTooLarge()
      if (when (buffer[i++]) {
        LF -> {
          if (buffer[i - 2] != CR) return null
          if (i - 2 == j){
            buffer.get()
            buffer.get()
            true
          }
          else {
            val headerBytes = ByteArray(i - j - 2)
            buffer.get(headerBytes)
            headers.add(String(headerBytes, Charsets.ISO_8859_1))
            buffer.get()
            buffer.get()
            j = i
            false
          }
        }
        else -> false
      }) break
      if (i == buffer.limit()) {
        if (exhausted) return null
        buffer.compact()
        if (buffer.position() == buffer.capacity()) throw HeadersTooLarge()
        exhausted = buffer.remaining() > socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS)
        buffer.flip()
        i -= j
        j = 0
      }
    }
    return exhausted
  }

  internal suspend fun body(socket: AsynchronousSocketChannel,
                            alreadyExhausted: Boolean,
                            buffer: ByteBuffer,
                            compliance: HttpHandler.Compliance,
                            headers: Headers,
                            context: HttpHandler.Context): Int? {
    var exhausted = alreadyExhausted
    buffer.compact().flip()
    val encoding = headers.value(TRANSFER_ENCODING)
    if (encoding == null || encoding == IDENTITY) {
      val contentLength = headers.value(CONTENT_LENGTH)?.toInt() ?: 0
      if (buffer.limit() > contentLength) return Statuses.BAD_REQUEST
      if (contentLength > 0) {
        if (!compliance.bodyAllowed) return Statuses.BAD_REQUEST
        val compression = headers.value(CONTENT_ENCODING)
        if (compression != null && compression != IDENTITY) return Statuses.UNSUPPORTED_MEDIA_TYPE
        if (contentLength > buffer.capacity()) return Statuses.PAYLOAD_TOO_LARGE
        if (headers.value(EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
          if (buffer.remaining() > 0) return Statuses.UNSUPPORTED_MEDIA_TYPE
          socket.aWrite(context.CONTINUE.rewind() as ByteBuffer)
          exhausted = false
        }
        if (!exhausted && contentLength > buffer.limit()) {
          val limit = buffer.limit()
          buffer.position(limit).limit(buffer.capacity())
          socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
          buffer.limit(buffer.position()).position(limit)
          if (buffer.limit() > contentLength) return Statuses.BAD_REQUEST
        }
      }
    }
    else if (encoding == CHUNKED) {
      if (!compliance.bodyAllowed) return Statuses.BAD_REQUEST
      if (headers.value(EXPECT)?.toLowerCase() == ONE_HUNDRED_CONTINUE) {
        if (buffer.remaining() > 0) return Statuses.UNSUPPORTED_MEDIA_TYPE
        socket.aWrite(context.CONTINUE.rewind() as ByteBuffer)
        exhausted = false
      }
      // Body with chunked encoding
      // CHUNK_1_LENGTH_HEX\r\n
      // CHUNK_1_BYTES\r\n
      // ...
      // CHUNK_N_LENGTH_HEX\r\n
      // CHUNK_N_BYTES\r\n
      // 0\r\n
      // FIELD_NAME_1: FIELD_VALUE_1\r\n
      // ...
      // FIELD_NAME_N: FIELD_VALUE_N\r\n
      // \r\n
      // Trailing header fields are ignored.
      val sb = StringBuilder(12)
      var start = buffer.position()
      chunks@ while (true) { // for each chunk
        // Look for \r\n to extract the chunk length
        bytes@ while (true) {
          if (buffer.remaining() == 0) {
            if (exhausted) return Statuses.BAD_REQUEST
            val limit = buffer.limit()
            if (buffer.capacity() == limit) return Statuses.PAYLOAD_TOO_LARGE
            exhausted = buffer.remaining() > socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS)
            buffer.limit(buffer.position()).position(limit)
            if (buffer.remaining() == 0) return Statuses.BAD_REQUEST
          }
          val b = buffer.get()
          if (b == LF) { // End of chunk size line
            if (sb.last().toByte() != CR) return Statuses.BAD_REQUEST
            val index = sb.indexOf(';') // ignore chunk extensions
            val chunkSize = Integer.parseInt(
              if (index == -1) sb.trim().toString() else sb.substring(0, index).trim(),
              16
            )
            // remove chunk size line bytes from the buffer, and skip the chunk bytes
            sb.delete(0, sb.length)
            val end = buffer.position()
            val limit = buffer.limit()
            buffer.position(start)
            (buffer.slice().position(end - start) as ByteBuffer).compact()
            buffer.limit(limit - end + start)
            if (buffer.capacity() - start < chunkSize + 2) return Statuses.PAYLOAD_TOO_LARGE
            if (buffer.limit() < start + chunkSize + 2) {
              if (exhausted) return Statuses.BAD_REQUEST
              buffer.position(buffer.limit())
              exhausted = buffer.remaining() > socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS)
              buffer.limit(buffer.position())
              if (buffer.limit() < start + chunkSize + 2) return Statuses.BAD_REQUEST
            }
            buffer.position(start + chunkSize)
            // chunk bytes should be followed by \r\n
            if (buffer.get() != CR || buffer.get() != LF) return Statuses.BAD_REQUEST
            if (chunkSize == 0) {
              // zero length chunk marks the end of the chunk list
              // skip trailing fields (look for \r\n\r\n sequence)
              val last = buffer.position() - 2
              if (last > buffer.capacity() - 4) return Statuses.PAYLOAD_TOO_LARGE
              while (true) {
                if (buffer.remaining() == 0) {
                  if (exhausted) break
                  buffer.position(last + 2)
                  exhausted = buffer.remaining() > socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS)
                  buffer.limit(buffer.position()).position(last + 2)
                }
                if (b == LF) {
                  val position = buffer.position()
                  if (buffer[position - 1] == CR && buffer[position - 2] == LF) break
                }
              }
              buffer.limit(last).position(0)
              break@chunks
            }
            start = buffer.position() - 2
            break@bytes
          }
          sb.append(b.toChar())
        }
      }
    }
    return null
  }

  private val CR: Byte = 0x0d
  private val LF: Byte = 0x0a
  private val SPACE: Byte = 0x20
  private val HTAB: Byte = 0x09
  private val H_UPPER: Byte = 0x48
  private val T_UPPER: Byte = 0x54
  private val P_UPPER: Byte = 0x50
  private val SLASH: Byte = 0x2f
  private val ONE: Byte = 0x31
  private val DOT: Byte = 0x2e
  private val UNDERSCORE: Byte = 0x5f
  private val COLON: Byte = 0x3a
  private val SEMICOLON: Byte = 0x3b
  private val EQUALS: Byte = 0x3d
  private val QUESTION_MARK: Byte = 0x3f
  private val EXCLAMATION_POINT: Byte = 0x21
  private val AT: Byte = 0x40
  private val LEFT_SQUARE_BRACKET: Byte = 0x5b
  private val RIGHT_SQUARE_BRACKET: Byte = 0x5b
  private val DOUBLE_QUOTE: Byte = 0x22
  private val LOWER_THAN: Byte = 0x3C
  private val GREATER_THAN: Byte = 0x3C
  private val BACKSLASH: Byte = 0x5c
  private val BACKTICK: Byte = 0x60
  private val LEFT_CURLY_BRACE: Byte = 0x7b
  private val TILDA: Byte = 0x7e

  private val EXPECT = "Expect"
  private val ONE_HUNDRED_CONTINUE = "100-continue"
  private val CONTENT_LENGTH = "Content-Length"
  private val CONTENT_ENCODING = "Content-Encoding"
  private val TRANSFER_ENCODING = "Transfer-Encoding"
  private val IDENTITY = "identity"
  private val CHUNKED = "chunked"

  private fun validMethod(b: Byte): Boolean {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    return b > AT && b < LEFT_SQUARE_BRACKET
  }

  private fun validUrl(b: Byte): Boolean {
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    return b == EXCLAMATION_POINT || (b > DOUBLE_QUOTE && b < LOWER_THAN) || b == EQUALS ||
           (b > GREATER_THAN && b < BACKSLASH) || b == RIGHT_SQUARE_BRACKET || b == UNDERSCORE ||
           (b > BACKTICK && b < LEFT_CURLY_BRACE) || b == TILDA
  }

  internal class HeadersTooLarge : Exception()

}
