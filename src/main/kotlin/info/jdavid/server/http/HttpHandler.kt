package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

abstract class HttpHandler<A: HttpHandler.Acceptance, C: AbstractHttpHandler.Context>: AbstractHttpHandler<A, C>() {

  final suspend override fun handle(acceptance: A,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    socket: AsynchronousSocketChannel,
                                    context: C) {
    val response = handle(acceptance, headers, body, context)
    response.write(socket, body)
  }

  abstract fun handle(acceptance: A,
                      headers: Headers,
                      body: ByteBuffer,
                      context: C): Response<*>

  abstract class Response<U>(val statusCode: Int) {
    val headers = Headers()
    var body: U? = null
    fun header(name: String, value: String): Response<U> {
      headers.add(name, value)
      return this
    }
    fun header(name: String) = headers.value(name)
    fun body(body: U): Response<U> {
      this.body = body
      return this
    }
    fun noBody(): Response<U> {
      body = null
      return this
    }
    protected abstract fun bodyMediaType(): String?
    protected abstract fun bodyByteLength(): Long
    protected abstract fun writeBody(buffer: ByteBuffer)
    private suspend fun error(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      buffer.put(ERROR_RESPONSE)
      socket.aWrite(buffer.flip() as ByteBuffer, 5000L, TimeUnit.MILLISECONDS)
    }
    internal suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      buffer.clear()
      val statusMessage = Status.HTTP_STATUSES[statusCode] ?: return error(socket, buffer)
      if (body != null) {
        val contentLength = bodyByteLength()
        if (contentLength > buffer.capacity()) return error(socket, buffer)
        headers.set(Headers.CONTENT_LENGTH, contentLength.toString())
        headers.set(Headers.CONTENT_TYPE, bodyMediaType() ?: MediaType.OCTET_STREAM)
      }
      buffer.put("HTTP/1.1 ${statusCode} ${statusMessage}\r\n".toByteArray(Charsets.ISO_8859_1))
      for (line in headers.lines) {
        buffer.put(line.toByteArray(Charsets.ISO_8859_1))
        buffer.put(CRLF)
      }
      buffer.put(CRLF)
      socket.aWrite(buffer.flip() as ByteBuffer, 5000L, TimeUnit.MILLISECONDS)

      if (body != null) {
        buffer.clear()
        writeBody(buffer)
        socket.aWrite(buffer.flip() as ByteBuffer,
                      5000L + buffer.capacity() / 1000, TimeUnit.MILLISECONDS)
      }
    }
  }

  open class Acceptance(bodyAllowed: Boolean,
                        bodyRequired: Boolean,
                        val method: Method,
                        val uri: String): Handler.Acceptance(bodyAllowed, bodyRequired)

  internal companion object {
    val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
    val ERROR_RESPONSE =
      ("HTTP/1.1 ${Status.INTERNAL_SERVER_ERROR} ${Status.HTTP_STATUSES[Status.INTERNAL_SERVER_ERROR]}\r\n" +
       "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").
        toByteArray(Charsets.US_ASCII)

  }

}
