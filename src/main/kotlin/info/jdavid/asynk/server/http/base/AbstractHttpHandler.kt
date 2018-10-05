@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import info.jdavid.asynk.http.internal.Http
import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.http.Acceptance
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

/**
 * Abstract handler for http requests.
 * @param ACCEPTANCE the Acceptance object returned when accepting the connection.
 * @param CONTEXT the thread-level context object that is shared by all instances of this handler
 * running on the same thread.
 */
abstract class AbstractHttpHandler<ACCEPTANCE: Acceptance,
                                   CONTEXT: AbstractHttpHandler.Context>: Handler<CONTEXT> {

  private val logger = LoggerFactory.getLogger(AbstractHttpHandler::class.java)

  final override suspend fun handle(socket: AsynchronousSocketChannel,
                                    buffer: ByteBuffer,
                                    context: CONTEXT) {
    buffer.clear()
    if (withTimeout(20000L) { socket.asyncRead(buffer) } < 16) return reject(socket, buffer, context)
    buffer.flip()
    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
    val uri = Http.uri(buffer) ?: return reject(socket, buffer, context)
    logger.info(uri)
    val acceptance = acceptUri(method, uri) ?: return notFound(socket, buffer, context)
    if (buffer.remaining() < 4) {
      buffer.compact()
      if (withTimeout(20000L) { socket.asyncRead(buffer) } < 4) return reject(socket, buffer, context)
      buffer.flip()
    }
    val headers = Headers()
    try {
      if (!Http.headers(socket, buffer, headers)) return reject(socket, buffer, context)
    }
    catch (ignore: Http.HeadersTooLarge) {
      return response(socket, context.REQUEST_HEADER_FIELDS_TOO_LARGE)
    }
    val code =
      Http.body(socket, Http.Version.HTTP_1_1, buffer,
                acceptance.bodyAllowed, acceptance.bodyRequired, headers, context.CONTINUE)
    if (code != null) return response(socket, context.response(code))
    try {
      handle(acceptance, headers, buffer, socket, context)
    }
    catch (e: Exception) {
      logger.warn("Handling ${method} ${uri} failed", e)
      serverError(socket, buffer, context)
    }
  }

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    logger.info(remoteAddress.hostString)
    return true
  }

  /**
   * Returns whether this handler can handle an http request to the specified uri with the specified http
   * method by either returning null (it can't) or an acceptance object.
   * @param method the http method used for the request.
   * @param uri the http request uri.
   * @return the acceptance object, or null if the request is not accepted.
   */
  abstract suspend fun acceptUri(method: Method, uri: String): ACCEPTANCE?

  /**
   * Request handler method, responsible for writing the request response to the socket.
   * @param acceptance the acceptance object returned by [acceptUri].
   * @param headers the request headers.
   * @param body the request body as a ByteBuffer (may not contain any data). It can be reused to write
   * the response to the socket. The buffer size is maxRequestSize.
   * @param context the thread-level context object that is shared by all instances of this handler running on
   * the same thread.
   */
  abstract suspend fun handle(acceptance: ACCEPTANCE, headers: Headers, body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: CONTEXT)

  /***
   * Writes the response for a rejected ([connect] returned false) request
   * (sends a 400 Bad Request by default).
   */
  open suspend fun reject(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    badRequest(socket, buffer, context)
  }

  /***
   * Writes the response for an invalid request (sends a 400 Bad Request by default).
   */
  open suspend fun badRequest(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    response(socket, context.BAD_REQUEST)
  }

  /***
   * Writes the response for a request that was not accepted (sends a 400 Bad Request by default).
   */
  open suspend fun notFound(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    response(socket, context.NOT_FOUND)
  }

  /**
   * Writes the response for a request that was accepted but the [handle] method threw an exception
   * (sends a 500 Server Error by default).
   */
  open suspend fun serverError(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT) {
    response(socket, context.INTERNAL_SERVER_ERROR)
  }

  private suspend fun response(socket: AsynchronousSocketChannel, payload: ByteBuffer) {
    payload.rewind()
    socket.asyncWrite(payload, true)
  }

  /**
   * Abstract Context class for http handlers (contains some prebuilt responses).
   */
  @Suppress("PropertyName", "unused", "MemberVisibilityCanBePrivate")
  open class Context private constructor(other: Context? = null) {
    constructor(others: Collection<*>?): this(others?.find { it is Context } as? Context)
    val OK: ByteBuffer =
      other?.OK ?: emptyResponse(Status.OK)
    val REQUEST_HEADER_FIELDS_TOO_LARGE: ByteBuffer =
      other?.REQUEST_HEADER_FIELDS_TOO_LARGE ?: emptyResponse(Status.REQUEST_HEADER_FIELDS_TOO_LARGE)
    val PAYLOAD_TOO_LARGE: ByteBuffer =
      other?.PAYLOAD_TOO_LARGE ?: emptyResponse(Status.PAYLOAD_TOO_LARGE)
    val UNSUPPORTED_MEDIA_TYPE: ByteBuffer =
      other?.UNSUPPORTED_MEDIA_TYPE ?: emptyResponse(Status.UNSUPPORTED_MEDIA_TYPE)
    val NOT_IMPLEMENTED: ByteBuffer =
      other?.NOT_IMPLEMENTED ?: emptyResponse(Status.NOT_IMPLEMENTED)
    val BAD_REQUEST: ByteBuffer =
      other?.BAD_REQUEST ?: emptyResponse(Status.BAD_REQUEST)
    val FORBIDDEN: ByteBuffer =
      other?.FORBIDDEN ?: emptyResponse(Status.FORBIDDEN)
    val NOT_FOUND: ByteBuffer =
      other?.NOT_FOUND ?: emptyResponse(Status.NOT_FOUND)
    val INTERNAL_SERVER_ERROR: ByteBuffer =
      other?.INTERNAL_SERVER_ERROR ?: emptyResponse(Status.INTERNAL_SERVER_ERROR)
    val CONTINUE: ByteBuffer =
      other?.CONTINUE ?: "HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII).let {
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
