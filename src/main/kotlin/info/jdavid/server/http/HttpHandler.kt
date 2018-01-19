package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

abstract class HttpHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                           CONTEXT: AbstractHttpHandler.Context,
                           out PARAMS: Any>(
  val route: Route<PARAMS>?
): AbstractHttpHandler<ACCEPTANCE, CONTEXT>() {

  final override suspend fun handle(acceptance: ACCEPTANCE,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    socket: AsynchronousSocketChannel,
                                    context: CONTEXT) {
    val response = handle(acceptance, headers, body, context)
    response.write(socket, body)
  }

  abstract suspend fun handle(acceptance: ACCEPTANCE,
                              headers: Headers,
                              body: ByteBuffer,
                              context: CONTEXT): Response<*>

  abstract class Response<BODY>(val statusCode: Int) {
    val headers = Headers()
    var body: BODY? = null
    fun header(name: String, value: String): Response<BODY> {
      headers.add(name, value)
      return this
    }
    fun header(name: String) = headers.value(name)
    fun body(body: BODY): Response<BODY> {
      this.body = body
      return this
    }
    fun noBody(): Response<BODY> {
      body = null
      return this
    }
    protected abstract fun bodyMediaType(): String?
    protected abstract suspend fun bodyByteLength(): Long
    protected abstract suspend fun writeBody(buffer: ByteBuffer)
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

  open class Acceptance<out PARAMS>(bodyAllowed: Boolean,
                                    bodyRequired: Boolean,
                                    val method: Method,
                                    val uri: String,
                                    val routeParams: PARAMS?): Handler.Acceptance(bodyAllowed, bodyRequired)

  interface Route<out PARAMS> {
    fun match(method: Method, uri: String): PARAMS?
  }

  internal companion object {
    val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
    val ERROR_RESPONSE =
      ("HTTP/1.1 ${Status.INTERNAL_SERVER_ERROR} ${Status.HTTP_STATUSES[Status.INTERNAL_SERVER_ERROR]}\r\n" +
       "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").
        toByteArray(Charsets.US_ASCII)
  }

}
