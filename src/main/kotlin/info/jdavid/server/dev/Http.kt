package info.jdavid.server.dev

import info.jdavid.server.http.http11.Headers
import kotlinx.coroutines.experimental.nio.aRead
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

object Http {

  internal suspend fun request(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // 1. look for first space -> METHOD
    // 2. look for second space -> URI
    // 3. check that rest of line is correct.

    var exhausted = buffer.remaining() > socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
    buffer.flip()
    // Shortest possible request line is 16 bytes long
    if (buffer.remaining() < 16) return

    // 1. look for first space to extract METHOD
    var i = 0
    while (true) {
      if (i == 7) return
      val b = buffer[i++]
      if (validMethod(b)) continue
      if (b == SPACE) break
      return
    }
    val methodBytes = ByteArray(i-1)
    buffer.get(methodBytes)
    val method = String(methodBytes, Charsets.US_ASCII)
    buffer.get()

    // 2. look for second space to extract URI
    var j = i
    while (true) {
      if (i == buffer.remaining()) return
      val b = buffer[i++]
      if (validUrl(b)) continue
      if (b == SPACE) break
      return
    }
    val uriBytes = ByteArray(i-j-1)
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
        buffer.get() != LF) return

    println("${method} ${uri}")

    // Headers
    // FIELD_NAME_1: FIELD_VALUE_1\r\n
    // ...
    // FIELD_NAME_N: FIELD_VALUE_N\r\n
    // \r\n
    // Add content between \r\n as header lines until an empty line signifying the end of the headers.
    val headers = Headers()
    i = buffer.position()
    j = i
    while (true) {
      if (when (buffer[i++]) {
        LF -> {
          if (buffer[i-2] != CR) return
          if (i-2 == j){
            buffer.get()
            buffer.get()
            true
          }
          else {
            val headerBytes = ByteArray(i-j-2)
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
        if (exhausted) return
        buffer.position(j)
        buffer.compact()
        buffer.position(i-j)
        exhausted = buffer.remaining() > socket.aRead(buffer, 3000L, TimeUnit.MILLISECONDS)
        buffer.flip()
        buffer.position(0)
        i -= j
        j = 0
      }
    }

    println(headers.lines.joinToString("\n"))

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

}
