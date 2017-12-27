package info.jdavid.server.dev

import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

internal open class HttpHandler: Handler {

  suspend override fun context(): Any? = Context()

  override suspend fun connect(remoteAddress: InetSocketAddress): Boolean {
    println(remoteAddress.hostString)
    return true
  }

  final override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    val exhausted = buffer.remaining() > socket.aRead(buffer, 5000L, TimeUnit.MILLISECONDS)
    buffer.flip()
    val method = Http.method(buffer) ?: return reject(socket, buffer, context)
    val path = Http.path(buffer) ?: return reject(socket, buffer, context)

    val compliance = acceptPath(method, path) ?: return notFound(socket, buffer, context)

    val headers = Http.headers(socket, exhausted, buffer) ?: return reject(socket, buffer, context)

    response(socket, (context as Context).OK)

    println("${method} ${path}")
    println(headers.lines.joinToString("\n"))
  }

  open suspend fun acceptPath(method: Method, path: String): Compliance? {
    return NoBodyAllowed
  }

  open suspend fun reject(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    response(socket, (context as Context).BAD_REQUEST)
  }

  open suspend fun notFound(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?) {
    response(socket, (context as Context).NOT_FOUND)
  }

  private suspend fun response(socket: AsynchronousSocketChannel, payload: ByteBuffer) {
    socket.aWrite(payload.rewind() as ByteBuffer)
  }

  private class Context {
    val OK =
      "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".
      toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
    val BAD_REQUEST =
      "HTTP/1.1 400 BAD REQUEST\r\nContent-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".
        toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
      }
    val NOT_FOUND =
      "HTTP/1.1 404 NOT FOUND\r\nContent-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".
        toByteArray(Charsets.US_ASCII).let {
        val bytes = ByteBuffer.allocateDirect(it.size); bytes.put(it); bytes
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
