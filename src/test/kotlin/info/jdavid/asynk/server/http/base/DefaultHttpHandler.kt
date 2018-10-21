package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Crypto
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

open class DefaultHttpHandler(maxRequestSize: Int = 4096): SimpleHttpHandler(maxRequestSize) {

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

    //val extra = if (isText) body.remaining() else Math.min(2048, body.remaining() * 2)
    val extra = if (isText) body.remaining() else body.remaining() * 2
    val bytes = str.toString().toByteArray(Charsets.ISO_8859_1)
    socket.asyncWrite(
      ByteBuffer.wrap(
        "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size + extra}\r\nConnection: close\r\n\r\n".
          toByteArray(Charsets.US_ASCII)
      ),
      true
    )
    socket.asyncWrite(ByteBuffer.wrap(bytes), true)
    if (extra > 0) {
      if (contentType.startsWith("text/") ||
          contentType.startsWith("application/") &&
          (contentType.startsWith(MediaType.JAVASCRIPT) ||
           contentType.startsWith(MediaType.JSON) ||
           contentType.startsWith(MediaType.XHTML) ||
           contentType.startsWith(MediaType.WEB_MANIFEST))) {
        socket.asyncWrite(body, true)
      }
      else {
//        if (body.remaining() > 1024) {
//          val limit = body.limit()
//          body.limit(body.position() + 511)
//          socket.asyncWrite(
//            ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)),
//            true
//          )
//          socket.asyncWrite(
//            ByteBuffer.wrap("....".toByteArray(Charsets.US_ASCII)),
//            true
//          )
//          body.limit(limit)
//          body.position(limit - 511)
//          socket.asyncWrite(
//            ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)),
//            true
//          )
//        }
//        else {
          socket.asyncWrite(ByteBuffer.wrap(Crypto.hex(body).toByteArray(Charsets.US_ASCII)), true)
//        }
      }
    }
  }

}
