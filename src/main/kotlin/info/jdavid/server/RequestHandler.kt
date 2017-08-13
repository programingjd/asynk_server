package info.jdavid.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface RequestHandler {

  suspend fun handle(channel: AsynchronousSocketChannel, address: InetSocketAddress,
                     readTimoutMillis: Long, writeTimeoutMillis: Long,
                     maxHeaderSize: Int,
                     segment: ByteBuffer, buffer: ByteBuffer)

  companion object {
    val DEFAULT = object: HttpRequestHandler() {
      override fun acceptConnection(address: InetSocketAddress): Int {
        println("Client: ${address.address}")
        return super.acceptConnection(address)
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

      override suspend fun handle(address: InetSocketAddress,
                                  method: String,
                                  uri: String,
                                  headers: Headers,
                                  channel: AsynchronousSocketChannel,
                                  writeTimeoutMillis: Long,
                                  segment: ByteBuffer,
                                  buffer: ByteBuffer) {
        handleError(channel, writeTimeoutMillis, 200)
      }
    }
  }

}
