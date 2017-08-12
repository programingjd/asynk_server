package info.jdavid.server

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit


open class HttpRequestHandler: RequestHandler {

  suspend override fun handle(channel: AsynchronousSocketChannel, address: InetSocketAddress,
                              readTimoutMillis: Long, writeTimeoutMillis: Long,
                              maxHeaderSize: Int,
                              segment: ByteBuffer, buffer: ByteBuffer) {
    if (!acceptConnection(address)) return channel.close()
    val segmentSize = segment.capacity()
    var capacity = buffer.capacity()
    if (capacity < segmentSize) {
      throw RuntimeException("The maximum request size is lower than the maximum size of the status line.")
    }
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimoutMillis)

    try {
      // Status Line
      var length = channel.aRead(segment, readTimoutMillis, TimeUnit.MILLISECONDS)
      if (length < 16) return channel.close()
      var exhausted = length < segmentSize
      var offset = segment.arrayOffset()
      var array = segment.array()
      println(String(array))
      var i = 0
      while (true) {
        if (i == 7) return channel.close()
        val b = array[offset + i++]
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (validMethod(b)) continue
        if (b == SPACE) break
        return channel.close()
      }
      val method = String(array, offset, i - 1)
      println("method: '${method}'")
      var j = i
      while (true) {
        if (j == length) return handleError(channel, writeTimeoutMillis, 414)
        val b = array[offset + j++]
        if (validUrl(b)) continue
        if (b == SPACE) break
        return channel.close()
      }
      val uri = String(array, offset + i, j - ++i)
      println ("uri: '${uri}'")
      i += uri.length

      if (array[offset + i++] != H_UPPER ||
          array[offset + i++] != T_UPPER ||
          array[offset + i++] != T_UPPER ||
          array[offset + i++] != P_UPPER ||
          array[offset + i++] != SLASH ||
          array[offset + i++] != ONE ||
          array[offset + i++] != DOT ||
          array[offset + i++] != ONE ||
          array[offset + i++] != CR ||
          array[offset + i++] != LF) {
        return channel.close()
      }

      // Headers + Body
      buffer.put(array, offset + i, length - i)
      var size = length - i
      capacity -= size
      while (!exhausted) {
        segment.rewind()
        length = channel.aRead(segment, deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        if (length < 0) {
          exhausted = true
          break
        }
        capacity -= length
        if (capacity < 0) return handleError(channel, writeTimeoutMillis, 413)
        size += length
        buffer.put(segment)
        if (segment.limit() != segmentSize) {
          exhausted = true
          break
        }
        if (size > maxHeaderSize) break
      }

      // Headers
      val headers = ArrayList<String>()
      offset = buffer.arrayOffset()
      array = buffer.array()
      length = buffer.limit()
      i = 0
      j = 0
      while (true) {
        if (i == length) return handleError(channel, writeTimeoutMillis, 431)
        if (when (array[offset + i++]) {
          LF -> {
            if (array[offset + i - 2] != CR) return handleError(channel, writeTimeoutMillis, 400)
            if (i - 2 == j) true else {
              headers.add(String(array, j, i - j - 2))
              j = i
              false
            }
          }
          else -> false
        }) break
      }

      println("headers:")
      headers.forEach {
        println("  ${it}")
      }
      handleError(channel, writeTimeoutMillis, 200)

    }
    catch (e: InterruptedByTimeoutException) {
      handleError(channel, writeTimeoutMillis, 408)
    }
    println(RequestHandler.counter.incrementAndGet())
    channel.close()
  }

  private suspend fun handleError(channel: AsynchronousSocketChannel, writeTimeoutMillis: Long, code: Int) {
    val message = HTTP_STATUSES.get(code) ?: throw IllegalArgumentException()
    try {
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
      channel.aWrite(ByteBuffer.wrap("HTTP/1.1 ${code} ${message}\r\n".toByteArray(ASCII)),
                     writeTimeoutMillis, TimeUnit.MILLISECONDS)
      val timeout = deadline - System.nanoTime()
      if (timeout > 0L) channel.aWrite(ByteBuffer.wrap(EMPTY_BODY_HEADER), timeout, TimeUnit.NANOSECONDS)
    }
    catch (e: InterruptedByTimeoutException) {}
    channel.close()
  }

  protected fun acceptConnection(address: InetSocketAddress) = true

  companion object {
    val HTTP_STATUSES = mapOf(
      200 to "OK",
      301 to "Moved Permanently",
      302 to "Found",
      304 to "Not Modified",
      307 to "Temporary Redirect",
      308 to "Permanent Redirect",
      400 to "Bad Request",
      401 to "Unauthorized",
      403 to "Forbidden",
      404 to "Not Found",
      405 to "Method Not Allowed",
      408 to "Request Timeout",
      410 to "Gone",
      413 to "Payload Too Large",
      414 to "URI Too Long",
      431 to "Request Header Fields Too Large"
    )
    val ASCII = Charsets.US_ASCII
    val UTF_8 = Charsets.UTF_8

    private val EMPTY_BODY_HEADER = "Content-Length: 0\r\n\r\n".toByteArray(ASCII)
    private val CR: Byte = 0x0d
    private val LF: Byte = 0x0a
    private val SPACE: Byte = 0x20
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

}
