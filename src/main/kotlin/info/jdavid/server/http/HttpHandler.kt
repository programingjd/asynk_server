package info.jdavid.server.http

import info.jdavid.server.Handler
import info.jdavid.server.Handler.Companion.hex
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

internal open class HttpHandler: Handler {

  final override suspend fun handle(socket: AsynchronousSocketChannel,
                                    buffer: ByteBuffer,
                                    context: Any?) {
    buffer.clear()
    var exhausted = buffer.remaining() > socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
    buffer.flip()
    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
    val path = Http.path(buffer) ?: return reject(socket, buffer, context)
    val compliance = acceptPath(method, path) ?: return notFound(socket, buffer, context)
    val headers = Headers()
    exhausted = try {
      Http.headers(socket, exhausted, buffer, headers) ?: return reject(socket, buffer, context)
    }
    catch (ignore: Http.HeadersTooLarge) {
      return response(socket, (context as Context).REQUEST_HEADER_FIELDS_TOO_LARGE)
    }
    val code = Http.body(socket, exhausted, buffer, compliance, headers, context as Context)
    if (code != null) return response(socket, context.response(code))
    handle(method, path, headers, buffer, socket, context)
  }

  override suspend fun context() = Context()

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    println(remoteAddress.hostString)
    return true
  }

  open suspend fun acceptPath(method: Method, path: String): Compliance? {
    when (method) {
      is Method.OPTIONS -> return NoBodyAllowed
      is Method.HEAD -> return NoBodyAllowed
      is Method.GET -> return NoBodyAllowed
      is Method.POST -> return BodyRequired
      is Method.PUT -> return BodyRequired
      is Method.DELETE -> return BodyNotRequired
      is Method.PATCH -> return BodyRequired
      else -> return BodyNotRequired
    }
  }

  open suspend fun handle(method: Method, path: String, headers: Headers, body: ByteBuffer?,
                          socket: AsynchronousSocketChannel,
                          context: Any?) {
    val str = StringBuilder()
    str.append("${method} ${path}\r\n\r\n")
    str.append(headers.lines.joinToString("\r\n"))
    str.append("\n\n")
    val contentType = headers.value(Headers.CONTENT_TYPE) ?: "text/plain"
    val isText =
      contentType.startsWith("text/") ||
      contentType.startsWith("application/") &&
        (contentType.startsWith(MediaType.JAVASCRIPT) ||
         contentType.startsWith(MediaType.JSON) ||
         contentType.startsWith(MediaType.XHTML) ||
         contentType.startsWith(MediaType.WEB_MANIFEST))

    val extra = if (isText) { body?.remaining() ?: 0 } else { Math.min(2048, (body?.remaining() ?: 0) * 2) }
    val bytes = str.toString().toByteArray(Charsets.ISO_8859_1)
    socket.aWrite(ByteBuffer.wrap(
      "HTTP/1.1 200 OK\r\nContent-Type: plain/text\r\nContent-Length: ${bytes.size + extra}\r\nConnection: close\r\n\r\n".
        toByteArray(Charsets.US_ASCII)
    ))
    socket.aWrite(ByteBuffer.wrap(bytes))
    if (body != null) {
      if (contentType.startsWith("text/") ||
        contentType.startsWith("application/") &&
          (contentType.startsWith(MediaType.JAVASCRIPT) ||
            contentType.startsWith(MediaType.JSON) ||
            contentType.startsWith(MediaType.XHTML) ||
            contentType.startsWith(MediaType.WEB_MANIFEST))) {
        socket.aWrite(body)
      }
      else {
        if (body.remaining() > 1024) {
          val limit = body.limit()
          body.limit(body.position() + 511)
          socket.aWrite(ByteBuffer.wrap(hex(body).toByteArray(Charsets.US_ASCII)))
          socket.aWrite(ByteBuffer.wrap("....".toByteArray(Charsets.US_ASCII)))
          body.limit(limit)
          body.position(limit - 511)
          socket.aWrite(ByteBuffer.wrap(hex(body).toByteArray(Charsets.US_ASCII)))
        }
        else {
          socket.aWrite(ByteBuffer.wrap(hex(body).toByteArray(Charsets.US_ASCII)))
        }
      }
    }
  }

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
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
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

  interface Compliance {
    val bodyAllowed: Boolean
    val bodyRequired: Boolean
  }

  object NoBodyAllowed: Compliance {
    override val bodyAllowed = false
    override val bodyRequired = false
  }

  object BodyNotRequired: Compliance {
    override val bodyAllowed = true
    override val bodyRequired = false
  }

  object BodyRequired: Compliance {
    override val bodyAllowed = true
    override val bodyRequired = true
  }

}
