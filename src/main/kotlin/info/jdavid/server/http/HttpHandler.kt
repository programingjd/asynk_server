@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

abstract class HttpHandler<T: Handler.Acceptance>: Handler {

  final override suspend fun handle(socket: AsynchronousSocketChannel,
                                    buffer: ByteBuffer,
                                    context: Any?) {
    buffer.clear()
    var exhausted = buffer.remaining() > socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
    buffer.flip()
    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
    val uri = Http.uri(buffer) ?: return reject(socket, buffer, context)
    val acceptance = acceptUri(method, uri) ?: return notFound(socket, buffer, context)
    val headers = Headers()
    exhausted = try {
      Http.headers(socket, exhausted, buffer, headers) ?: return reject(socket, buffer, context)
    }
    catch (ignore: Http.HeadersTooLarge) {
      return response(socket, (context as Context).REQUEST_HEADER_FIELDS_TOO_LARGE)
    }
    val code = Http.body(socket, exhausted, buffer, acceptance, headers, context as Context)
    if (code != null) return response(socket, context.response(code))
    handle(acceptance, headers, buffer, socket, context)
  }

  override fun context() = Context()

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    println(remoteAddress.hostString)
    return true
  }

  abstract suspend fun acceptUri(method: Method, uri: String): T?

  abstract suspend fun handle(acceptance: T, headers: Headers, body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: Any?)

  open suspend fun reject(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    badRequest(socket, buffer, context)
  }

  open suspend fun badRequest(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    response(socket, (context as Context).BAD_REQUEST)
  }

  open suspend fun notFound(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    response(socket, (context as Context).NOT_FOUND)
  }

  private suspend fun response(socket: AsynchronousSocketChannel, payload: ByteBuffer) {
    socket.aWrite(payload.rewind() as ByteBuffer)
  }

  @Suppress("PropertyName", "MemberVisibilityCanPrivate", "unused")
  open class Context {
    val OK = emptyResponse(Status.OK)
    val REQUEST_HEADER_FIELDS_TOO_LARGE = emptyResponse(Status.REQUEST_HEADER_FIELDS_TOO_LARGE)
    val PAYLOAD_TOO_LARGE = emptyResponse(Status.PAYLOAD_TOO_LARGE)
    val UNSUPPORTED_MEDIA_TYPE = emptyResponse(Status.UNSUPPORTED_MEDIA_TYPE)
    val NOT_IMPLEMENTED = emptyResponse(Status.NOT_IMPLEMENTED)
    val BAD_REQUEST = emptyResponse(Status.BAD_REQUEST)
    val NOT_FOUND = emptyResponse(Status.NOT_FOUND)
    val CONTINUE = "HTTP/1.1 100 Continue\r\n\r\n".
      toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size) ?: throw RuntimeException(); bytes.put(it); bytes
      }
    internal fun response(code: Int): ByteBuffer {
      return when (code) {
        Status.BAD_REQUEST -> BAD_REQUEST
        Status.PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE
        Status.UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE
        Status.NOT_IMPLEMENTED -> NOT_IMPLEMENTED
        else -> throw IllegalArgumentException()
      }
    }
  }

  companion object {
    private fun emptyResponse(status: Int): ByteBuffer {
      val text =
        "HTTP/1.1 ${status} ${Status.HTTP_STATUSES[status]}\r\n" +
          "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
      return text.toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
    }
  }

}
