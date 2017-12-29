package info.jdavid.server.dev

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

internal open class HttpHandler: Handler {

  suspend override fun context() = Context()

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    println(remoteAddress.hostString)
    return true
  }

  final override suspend fun handle(socket: AsynchronousSocketChannel,
                                    buffer: ByteBuffer,
                                    context: Any?) {
    buffer.clear()
    var exhausted = buffer.remaining() > socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
    buffer.flip()
//    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
//    val path = Http.path(buffer) ?: return reject(socket, buffer, context)
    val method = Http.method(buffer)
    if (method == null) {
      println("method null")
      return reject(socket, buffer, context)
    }
    val path = Http.path(buffer)
    if (path == null) {
      println("path null")
      return reject(socket, buffer, context)
    }

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
    response(socket, (context as Context).OK)
    println("${method} ${path}")
    println(headers.lines.joinToString("\n"))
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
    val OK = emptyResponse(Statuses.OK)
    val REQUEST_HEADER_FIELDS_TOO_LARGE = emptyResponse(Statuses.REQUEST_HEADER_FIELDS_TOO_LARGE)
    val PAYLOAD_TOO_LARGE = emptyResponse(Statuses.PAYLOAD_TOO_LARGE)
    val UNSUPPORTED_MEDIA_TYPE = emptyResponse(Statuses.UNSUPPORTED_MEDIA_TYPE)
    val NOT_IMPLEMENTED = emptyResponse(Statuses.NOT_IMPLEMENTED)
    val BAD_REQUEST = emptyResponse(Statuses.BAD_REQUEST)
    val NOT_FOUND = emptyResponse(Statuses.NOT_FOUND)
    val CONTINUE = "HTTP/1.1 100 Continue\r\n\r\n".
      toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
    internal fun response(code: Int): ByteBuffer {
      return when (code) {
        Statuses.BAD_REQUEST -> BAD_REQUEST
        Statuses.PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE
        Statuses.UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE
        Statuses.NOT_IMPLEMENTED -> NOT_IMPLEMENTED
        else -> throw IllegalArgumentException()
      }
    }
  }

  companion object {
    private fun emptyResponse(status: Int): ByteBuffer {
      val text =
        "HTTP/1.1 ${status} ${Statuses.HTTP_STATUSES[status]}\r\n" +
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
