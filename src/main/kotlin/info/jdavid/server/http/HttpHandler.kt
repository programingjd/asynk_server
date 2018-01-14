package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

abstract class HttpHandler<T: Handler.Acceptance>: AbstractHttpHandler<T>() {

  final suspend override fun handle(acceptance: T,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    socket: AsynchronousSocketChannel,
                                    context: Any?) {
    val response = handle(acceptance, headers, body, context)
    response.write(socket, body)
  }

  abstract fun handle(acceptance: T,
                      headers: Headers,
                      body: ByteBuffer,
                      context: Any?): Response<T>

  abstract class Response<T>(val statusCode: Int) {
    val headers = Headers()
    var body: T? = null
    fun header(key: String, value: String): Response<T> {
      headers.add(key, value)
      return this
    }
    fun body(body: T): Response<T> {
      this.body = body
      return this
    }
    fun noBody(): Response<T> {
      body = null
      return this
    }
    protected abstract fun bodyMediaType(): String?
    protected abstract fun bodyByteLength(): Long
    protected abstract fun writeBody(buffer: ByteBuffer)
    private suspend fun error(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      buffer.put(Status.ERROR_RESPONSE.toByteArray(Charsets.US_ASCII))
      socket.aWrite(buffer.flip(), 5000L, TimeUnit.MILLISECONDS)
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
      socket.aWrite(buffer.flip(), 5000L, TimeUnit.MILLISECONDS)
      if (body != null) {
        buffer.clear()
        writeBody(buffer)
        socket.aWrite(buffer.flip(), 5000L + buffer.capacity() / 1000, TimeUnit.MILLISECONDS)
      }
    }
  }

}
