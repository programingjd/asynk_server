@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

abstract class AbstractHttpHandler<ACCEPTANCE: Handler.Acceptance,
                                   CONTEXT: AbstractHttpHandler.Context>: Handler<CONTEXT> {

  private val logger = LoggerFactory.getLogger(AbstractHttpHandler::class.java)

  abstract override fun context(): CONTEXT

  final override suspend fun handle(socket: AsynchronousSocketChannel,
                                    buffer: ByteBuffer,
                                    context: CONTEXT) {
    buffer.clear()
    var exhausted = buffer.remaining() > socket.aRead(buffer, 20000L, TimeUnit.MILLISECONDS)
    buffer.flip()
    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
    val uri = Http.uri(buffer) ?: return reject(socket, buffer, context)
    logger.info(uri)
    val acceptance = acceptUri(method, uri) ?: return notFound(socket, buffer, context)
    val headers = Headers()
    exhausted = try {
      Http.headers(socket, exhausted, buffer, headers) ?: return reject(socket, buffer, context)
    }
    catch (ignore: Http.HeadersTooLarge) {
      return response(socket, context.REQUEST_HEADER_FIELDS_TOO_LARGE)
    }
    val code = Http.body(socket, exhausted, buffer, acceptance, headers, context)
    if (code != null) return response(socket, context.response(code))
    handle(acceptance, headers, buffer, socket, context)
  }

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    logger.info(remoteAddress.hostString)
    return true
  }

  abstract suspend fun acceptUri(method: Method, uri: String): ACCEPTANCE?

  abstract suspend fun handle(acceptance: ACCEPTANCE, headers: Headers, body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: CONTEXT)

  open suspend fun reject(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    badRequest(socket, buffer, context)
  }

  open suspend fun badRequest(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    response(socket, context.BAD_REQUEST)
  }

  open suspend fun notFound(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    response(socket, context.NOT_FOUND)
  }

  private suspend fun response(socket: AsynchronousSocketChannel, payload: ByteBuffer) {
    socket.aWrite(payload.rewind() as ByteBuffer)
  }

  @Suppress("PropertyName", "unused", "MemberVisibilityCanBePrivate")
  open class Context {
    val OK = emptyResponse(Status.OK)
    val REQUEST_HEADER_FIELDS_TOO_LARGE = emptyResponse(Status.REQUEST_HEADER_FIELDS_TOO_LARGE)
    val PAYLOAD_TOO_LARGE = emptyResponse(Status.PAYLOAD_TOO_LARGE)
    val UNSUPPORTED_MEDIA_TYPE = emptyResponse(Status.UNSUPPORTED_MEDIA_TYPE)
    val NOT_IMPLEMENTED = emptyResponse(Status.NOT_IMPLEMENTED)
    val BAD_REQUEST = emptyResponse(Status.BAD_REQUEST)
    val FORBIDDEN = emptyResponse(Status.FORBIDDEN)
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
