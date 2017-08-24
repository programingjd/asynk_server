package info.jdavid.server

import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.InterruptedByTimeoutException


abstract class HttpRequestHandler: RequestHandler {

  suspend protected abstract fun handle(address: InetSocketAddress,
                                        method: String,
                                        uri: String,
                                        headers: Headers,
                                        channel: Channel,
                                        deadline: Long,
                                        buffer: ByteBuffer)

  override fun enableHttp2() = true

  // accept methods return values:
  //  -1       -> accept
  //   0       -> drop connection
  //  100..500 -> error code

  protected open fun acceptUri(method: String, uri: String): Int {
    return if (method == "GET" || method == "HEAD") -1 else 404
  }

  protected open fun acceptHeaders(method: String, uri: String, headers: Headers): Int {
    return if (headers.has("host")) -1 else 0
  }

  protected open fun acceptBody(method: String): Int = -1

  suspend final override fun connection(channel: Channel,
                                        readTimeoutMillis: Long, writeTimeoutMillis: Long): Closeable? {
    return if (channel is SecureChannel && channel.applicationProtocol() == "h2") {
      Http2Connection(channel, readTimeoutMillis, writeTimeoutMillis).start()
    }
    else null
  }

  suspend final override fun handle(channel: Channel,
                                    connection: Closeable?,
                                    address: InetSocketAddress,
                                    readDeadline: Long, writeDeadline: Long,
                                    maxHeaderSize: Int,
                                    buffer: ByteBuffer): Boolean {
    return if (connection is Http2Connection) {
      http2(channel, address, readDeadline, writeDeadline, maxHeaderSize, buffer)
    }
    else {
      http11(channel, address, readDeadline, writeDeadline, maxHeaderSize, buffer)
    }
  }

  suspend private fun http2(channel: Channel, address: InetSocketAddress,
                            readDeadline: Long, writeDeadline: Long,
                            maxHeaderSize: Int,
                            buffer: ByteBuffer): Boolean {
    try {
      return true
    }
    catch (e: InterruptedByTimeoutException) {
      return false
    }
  }

  suspend private fun http11(channel: Channel, address: InetSocketAddress,
                             readDeadline: Long, writeDeadline: Long,
                             maxHeaderSize: Int,
                             buffer: ByteBuffer): Boolean {
    try {
      var capacity = buffer.capacity()
      // Status Line
      var segment = channel.read(readDeadline)
      var length = segment.remaining()
      if (length < 16) return false
      var i = 0
      while (true) {
        if (i == 7) return false
        val b = segment[i++]
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (validMethod(b)) continue
        if (b == SPACE) break
        return false
      }
      val methodBytes = ByteArray(i-1)
      segment.get(methodBytes)
      val method = String(methodBytes, ASCII)
      segment.get()
      var j = i
      while (true) {
        if (i == length) return handleError(channel, writeDeadline, 414)
        val b = segment[i++]
        if (validUrl(b)) continue
        if (b == SPACE) break
        return false
      }
      val uriBytes = ByteArray(i-j-1)
      segment.get(uriBytes)
      val uri = String(uriBytes)
      segment.get()
      if (abort(channel, writeDeadline, acceptUri(method, uri))) return false
      if (segment.get() != H_UPPER ||
          segment.get() != T_UPPER ||
          segment.get() != T_UPPER ||
          segment.get() != P_UPPER ||
          segment.get() != SLASH ||
          segment.get() != ONE ||
          segment.get() != DOT ||
          segment.get() != ONE ||
          segment.get() != CR ||
          segment.get() != LF) return false
      // Headers + Body
      buffer.put(segment)
      // Headers
      val headers = Headers()
      length = buffer.position()
      buffer.flip()
      i = 0
      j = 0
      while (true) {
        if (i == length) {
          if (i > maxHeaderSize) return handleError(channel, writeDeadline, 431)
          segment = channel.read(readDeadline)
          length = segment.remaining()
          if (length == 0) return handleError(channel, writeDeadline, 400)
          buffer.put(segment)
          length = buffer.position()
        }
        if (when (buffer[i++]) {
          LF -> {
            if (buffer[i - 2] != CR) return handleError(channel, writeDeadline, 400)
            if (i - 2 == j) {
              buffer.get()
              buffer.get()
              true
            }
            else {
              val headerBytes = ByteArray(i-j-2)
              buffer.get(headerBytes)
              headers.add(String(headerBytes, ISO_8859_1))
              buffer.get()
              buffer.get()
              j = i
              false
            }
          }
          else -> false
        }) break
      }
      if (i > maxHeaderSize) return handleError(channel, writeDeadline, 431)
      if (abort(channel, writeDeadline, acceptHeaders(method, uri, headers))) return false
      buffer.compact()
      length = buffer.position()
      capacity -= length
      if (capacity < 0) return handleError(channel, writeDeadline, 413)
      val encoding = headers.value(Headers.TRANSFER_ENCODING)
      //if (encoding?.endsWith(CHUNKED) == true) return handleError(channel, writeTimeoutMillis, 501)
      if (encoding != null && encoding != IDENTITY) return handleError(channel, writeDeadline, 501)
      val contentLength = headers.value(Headers.CONTENT_LENGTH)?.toInt() ?: 0
      if (contentLength > length + capacity) return handleError(channel, writeDeadline, 413)
      capacity = contentLength - length
      // Rest of body
      while (capacity > 0) {
        segment.rewind()
        segment = channel.read(readDeadline)
        length = segment.remaining()
        if (length == 0) {
          break
        }
        capacity -= length
        if (capacity < 0) return handleError(channel, writeDeadline, 413)
        buffer.put(segment)
      }
      buffer.limit(buffer.position())
      buffer.position(0)
      val bytes = ByteArray(buffer.limit())
      buffer.get(bytes)
      println("Body:")
      println(String(bytes))
      handle(address, method, uri, headers, channel, writeDeadline, buffer)
      return true
    }
    catch (e: InterruptedByTimeoutException) {
      return handleError(channel, writeDeadline, 408)
    }
  }

  suspend private fun abort(channel: Channel, writeDeadline: Long,
                            acceptValue: Int): Boolean {
    if (acceptValue == -1) return false
    if (acceptValue == 0) return true
    else if (acceptValue in 100..500) {
      handleError(channel, writeDeadline, acceptValue)
      return true
    }
    throw IllegalArgumentException()
  }

  private suspend fun handleError(channel: Channel, writeDeadline: Long, code: Int): Boolean {
    val message = HTTP_STATUSES[code] ?: throw IllegalArgumentException()
    channel.write("HTTP/1.1 ${code} ${message}\r\n".toByteArray(ASCII), writeDeadline)
    channel.write(EMPTY_BODY_HEADER, writeDeadline)
    return false
  }

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
      431 to "Request Header Fields Too Large",
      500 to "Internal Server Error",
      501 to "Not Implemented"
    )

    val ASCII = Charsets.US_ASCII
    val UTF_8 = Charsets.UTF_8
    val ISO_8859_1 = Charsets.ISO_8859_1

    private val EMPTY_BODY_HEADER = "Content-Length: 0\r\n\r\n".toByteArray(ASCII)

    private val IDENTITY = "identity"
    private val CHUNKED = "chunked"

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
