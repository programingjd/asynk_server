package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.http.Crypto
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

open class DefaultHttpHandler: SimpleHttpHandler() {

  override suspend fun handle(acceptance: Acceptance, headers: Headers, body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: Context) {
    val str = StringBuilder()
    str.append("${acceptance.method} ${acceptance.uri}\r\n\r\n")
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

    val extra = if (isText) body.remaining() else Math.min(2048, body.remaining() * 2)
    val bytes = str.toString().toByteArray(Charsets.ISO_8859_1)
    ByteBuffer.wrap(
      "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size + extra}\r\nConnection: close\r\n\r\n".
        toByteArray(Charsets.US_ASCII)
    ).apply { while (remaining() > 0) socket.aWrite(this) }
    ByteBuffer.wrap(bytes).apply { while (remaining() > 0) socket.aWrite(this) }
    if (extra > 0) {
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
          ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)).apply {
            while (remaining() > 0) socket.aWrite(this)
          }
          ByteBuffer.wrap("....".toByteArray(Charsets.US_ASCII)).apply {
            while (remaining() > 0) socket.aWrite(this)
          }
          body.limit(limit)
          body.position(limit - 511)
          ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)).apply {
            while (remaining() > 0) socket.aWrite(this)
          }
        }
        else {
          ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)).apply {
            while (remaining() > 0) socket.aWrite(this)
          }
        }
      }
    }
  }

}
