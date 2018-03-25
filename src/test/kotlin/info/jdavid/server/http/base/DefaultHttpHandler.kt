package info.jdavid.server.http.base

import info.jdavid.server.Crypto
import info.jdavid.server.http.Headers
import info.jdavid.server.http.MediaType
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
    socket.aWrite(ByteBuffer.wrap(
      "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size + extra}\r\nConnection: close\r\n\r\n".
        toByteArray(Charsets.US_ASCII)
    ))
    socket.aWrite(ByteBuffer.wrap(bytes))
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
          socket.aWrite(ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)))
          socket.aWrite(ByteBuffer.wrap("....".toByteArray(Charsets.US_ASCII)))
          body.limit(limit)
          body.position(limit - 511)
          socket.aWrite(ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)))
        }
        else {
          socket.aWrite(ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)))
        }
      }
    }
  }

}
