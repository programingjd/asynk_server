package info.jdavid.server.http

import info.jdavid.server.Channel
import info.jdavid.server.Connection
import info.jdavid.server.ConnectionHandler
import info.jdavid.server.SecureChannel
import info.jdavid.server.http.http11.Headers
import info.jdavid.server.http.http2.Http2Connection
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLParameters
import kotlin.coroutines.experimental.CoroutineContext

abstract class HttpConnectionHandler(enableHttp2: Boolean): ConnectionHandler {

  private val protocols = if (enableHttp2) arrayOf("h2", "http/1.1") else arrayOf("http/1.1")

  suspend protected abstract fun handle(address: InetSocketAddress,
                                        method: String,
                                        uri: String,
                                        headers: Headers,
                                        channel: Channel,
                                        deadline: Long,
                                        buffer: ByteBuffer)

  override fun adjustSSLParameters(sslParameters: SSLParameters) {
    sslParameters.applicationProtocols = protocols
  }

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

  suspend final override fun connection(context: CoroutineContext, channel: Channel,
                                        readTimeoutMillis: Long, writeTimeoutMillis: Long): Connection? {
    return if (channel is SecureChannel && channel.applicationProtocol() == "h2") {
      Http2Connection(context, channel, readTimeoutMillis, writeTimeoutMillis).start()
    }
    else null
  }

  suspend final override fun handle(channel: Channel,
                                    connection: Connection?,
                                    address: InetSocketAddress,
                                    readTimeoutMillis: Long, writeTimeoutMillis: Long,
                                    maxHeaderSize: Int,
                                    buffer: ByteBuffer): Boolean {
    return if (connection is Http2Connection) {
      connection.stream(readTimeoutMillis, writeTimeoutMillis)
      return false
//        { http2(channel, address, readTimeoutMillis, writeTimeoutMillis, maxHeaderSize, buffer) }
//      )
    }
    else {
      http11(channel, address, readTimeoutMillis, writeTimeoutMillis, maxHeaderSize, buffer)
    }
  }

  suspend private fun http2(channel: Channel, address: InetSocketAddress,
                            readTimeoutMillis: Long, writeTimeoutMillis: Long,
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
                             readTimeoutMillis: Long, writeTimeoutMillis: Long,
                             maxHeaderSize: Int,
                             buffer: ByteBuffer): Boolean {
    val now = System.nanoTime()
    val readDeadline = now + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)
    val writeDeadline = now + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)

    // Request line: (ASCII)
    // METHOD URI HTTP/1.1\r\n
    // 1. look for first space -> METHOD
    // 2. look for second space -> URI
    // 3. check that rest of line is correct.

    try {
      var capacity = buffer.capacity()
      var segment = channel.read(readDeadline)
      var length = segment.remaining()
      // shortest possible request line is 16 bytes long.
      if (length < 16) return false

      // 1. look for first space to extract METHOD
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

      // 2. look for second space to extract URI
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

      // 3. HTTP/1.1\r\n should follow
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

      buffer.put(segment)

      // Headers
      // FIELD_NAME_1: FIELD_VALUE_1\r\n
      // ...
      // FIELD_NAME_N: FIELD_VALUE_N\r\n
      // \r\n
      // Add content between \r\n as header lines until an empty line signifying the end of the headers.
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

      // Body
      length = buffer.position()
      capacity -= length
      if (capacity < 0) return handleError(channel, writeDeadline, 413)
      val encoding = headers.value(Headers.TRANSFER_ENCODING)

      if (encoding == null || encoding == IDENTITY) {
        // Body with no encoding
        // Content-Length header specifies the amount of bytes to read.
        val contentLength = headers.value(Headers.CONTENT_LENGTH)?.toInt() ?: 0
        if (contentLength > 0 && abort(channel, writeDeadline, acceptBody(method))) return false
        if (contentLength > length + capacity) return handleError(channel, writeDeadline, 413)
        if (headers.value(Headers.EXPECT)?.toLowerCase() == CONTINUE) {
          // Special case for Expect: continue, intermediate 100 Continue response might be needed.
          if (length > 0 || contentLength == 0) return handleError(channel, writeDeadline, 400)
          channel.write(writeDeadline, CONTINUE_RESPONSE)
        }
        capacity = contentLength - length
        while (capacity > 0) {
          segment = channel.read(readDeadline)
          length = segment.remaining()
          if (length == 0) {
            break
          }
          capacity -= length
          if (capacity < 0) return handleError(channel, writeDeadline, 400)
          buffer.put(segment)
        }
        buffer.limit(buffer.position())
        buffer.position(0)
      }
      else if (encoding == CHUNKED) {
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
        if (abort(channel, writeDeadline, acceptBody(method))) return false
        if (headers.value(Headers.EXPECT)?.toLowerCase() == CONTINUE) {
          // Special case for Expect: continue, intermediate 100 Continue response might be needed.
          if (length > 0) return handleError(channel, writeDeadline, 400)
          channel.write(writeDeadline, CONTINUE_RESPONSE)
        }
        val sb = StringBuilder(12)
        var k = 0
        var max = buffer.position() - 1
        var p = 0
        while (true) {
          var n: Int
          // Look for \r\n to extract the chunk length
          while (true) {
            if (k > max) {
              segment = channel.read(readDeadline)
              length = segment.remaining()
              if (length == 0) return handleError(channel, writeDeadline, 400)
              capacity -= length
              if (capacity < 0) return handleError(channel, writeDeadline, 413)
              buffer.put(segment)
              max += length
            }
            val b = buffer[k++]
            sb.append(b.toChar())
            if (b == LF) {
              if (buffer[k - 2] != CR) return handleError(channel, writeDeadline, 400)
              var end = sb.length - 2
              var start = 0
              while (start < end) {
                val c = sb[start].toByte()
                if (c == SPACE || c == HTAB) ++start else break
              }
              while (end > start) {
                val c = sb[end].toByte()
                if (c == SPACE || c == HTAB) --end else break
              }
              n = Integer.parseInt(sb, start, end, 16)
              sb.delete(0, sb.length)
              break
            }
          }
          // Read chunk bytes
          while (max < k + n + 1) {
            segment = channel.read(readDeadline)
            length = segment.remaining()
            if (length == 0) return handleError(channel, writeDeadline, 400)
            capacity -= length
            if (capacity < 0) return handleError(channel, writeDeadline, 413)
            buffer.put(segment)
            max += length
          }
          k += n
          if (n == 0) {
            // zero length chunk marks the end of the chunk list
            // skip trailing fields and look for \r\n\r\n sequence
            while (true) {
              if (k > max) {
                segment = channel.read(readDeadline)
                length = segment.remaining()
                if (length == 0) return handleError(channel, writeDeadline, 400)
                capacity -= length
                if (capacity < 0) return handleError(channel, writeDeadline, 413)
                buffer.put(segment)
                max += length
              }
              val b = buffer[k++]
              if (b == LF) {
                if (buffer[k - 2] != CR) return handleError(channel, writeDeadline, 400)
                if (buffer[k - 3] == LF) break
              }
            }
            if (k < max) return handleError(channel, writeDeadline, 400)
            buffer.limit(p)
            break
          }
          else {
            // copy decoded bytes to body buffer
            val position = buffer.position()
            buffer.position(k - n)
            buffer.limit(k)
            val slice = buffer.slice()
            buffer.position(p)
            buffer.put(slice)
            p += n
            buffer.limit(buffer.capacity())
            buffer.position(position)
            if (buffer[k++] != CR || buffer[k++] != LF) return handleError(channel, writeDeadline, 400)
          }
        }
        buffer.limit(buffer.position())
        buffer.position(0)
      }
      else {
        return handleError(channel, writeDeadline, 501)
      }

      // Log body (TODO: remove)
      val bytes = ByteArray(buffer.limit())
      buffer.slice().get(bytes)
      println("Body:")
      println(String(bytes))

      handle(address, method, uri, headers, channel, writeDeadline, buffer)
      return headers.value(Headers.CONNECTION) == CLOSE
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
    channel.write(writeDeadline, "HTTP/1.1 ${code} ${message}\r\n".toByteArray(ASCII), ERROR_HEADERS)
    return false
  }

  companion object {
    val HTTP_STATUSES = mapOf(
      100 to "Continue",
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

    private val CONTINUE_RESPONSE = "HTTP/1.1 100 Continue\r\n\r\n".toByteArray(ASCII)
    private val ERROR_HEADERS = "Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(ASCII)

    private val CONTINUE = "100-continue"
    private val IDENTITY = "identity"
    private val CHUNKED = "chunked"
    private val CLOSE = "close"

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

}
