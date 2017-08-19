package info.jdavid.server

import kotlinx.coroutines.experimental.delay
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

interface RequestHandler {

  suspend fun connection(channel: Channel, readTimeoutMillis: Long, writeTimeoutMillis: Long): Closeable?

  fun enableHttp2(): Boolean

  suspend fun reject(address: InetSocketAddress): Boolean

  suspend fun handle(channel: Channel, connection: Closeable?, address: InetSocketAddress,
                     readDeadline: Long, writeDeadline: Long,
                     maxHeaderSize: Int, buffer: ByteBuffer): Boolean

  companion object {
    val DEFAULT = object: HttpRequestHandler() {
      suspend override fun reject(address: InetSocketAddress): Boolean {
        println("Client: ${address.address}")
        return false
      }
      override fun acceptUri(method: String, uri: String): Int {
        println("Method: ${method}")
        println("Uri: ${uri}")
        return super.acceptUri(method, uri)
      }
      override fun acceptHeaders(method: String, uri: String, headers: Headers): Int {
        println("Headers:")
        headers.lines.forEach { println("  ${it}") }
        return super.acceptHeaders(method, uri, headers)
      }
      suspend override fun handle(address: InetSocketAddress,
                                  method: String,
                                  uri: String,
                                  headers: Headers,
                                  channel: Channel,
                                  deadline: Long,
                                  buffer: ByteBuffer) {
        buffer.rewind().limit(buffer.capacity())
        buffer.put("HTTP/1.1 200 OK\r\n".toByteArray(ASCII))
        channel.write(buffer, deadline)
        val h = Headers()
        h.add(Headers.CONTENT_TYPE, "text/plain; charset=utf-8")
        h.add(Headers.CONTENT_LENGTH, "6")
        channel.write(h, deadline)
        buffer.put("body\n".toByteArray(UTF_8))
        channel.write(buffer, deadline)
        delay(5, TimeUnit.SECONDS)
        buffer.put("\n".toByteArray(UTF_8))
        channel.write(buffer, deadline)
      }
    }
  }

}
