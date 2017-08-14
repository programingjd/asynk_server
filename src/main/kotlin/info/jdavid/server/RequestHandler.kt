package info.jdavid.server

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.TimeUnit

interface RequestHandler {

  suspend fun reject(address: InetSocketAddress): Boolean

  suspend fun handle(channel: AsynchronousSocketChannel, address: InetSocketAddress,
                     readTimoutMillis: Long, writeTimeoutMillis: Long,
                     maxHeaderSize: Int,
                     segment: ByteBuffer, buffer: ByteBuffer): Boolean

  companion object {
    val DEFAULT = object: HttpRequestHandler() {
      override suspend fun reject(address: InetSocketAddress): Boolean {
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
      override suspend fun handle(address: InetSocketAddress,
                                  method: String,
                                  uri: String,
                                  headers: Headers,
                                  channel: AsynchronousSocketChannel,
                                  writeTimeoutMillis: Long,
                                  segment: ByteBuffer,
                                  buffer: ByteBuffer) {
        try {
          val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
          channel.aWrite(ByteBuffer.wrap("HTTP/1.1 200 OK\r\n".toByteArray(ASCII)),
                         writeTimeoutMillis, TimeUnit.MILLISECONDS)
          val timeout = deadline - System.nanoTime()
          if (timeout > 0L) channel.aWrite(
            ByteBuffer.wrap("Content-Type: text/plain\r\n".toByteArray()), timeout, TimeUnit.NANOSECONDS
          )
          if (timeout > 0L) channel.aWrite(
            ByteBuffer.wrap("Content-Length: 6\r\n".toByteArray()), timeout, TimeUnit.NANOSECONDS
          )
          if (timeout > 0L) channel.aWrite(
            ByteBuffer.wrap("\r\nbody\n".toByteArray()), timeout, TimeUnit.NANOSECONDS
          )
          delay(5, TimeUnit.SECONDS)
          if (timeout > 0L) channel.aWrite(
            ByteBuffer.wrap("\n".toByteArray()), timeout, TimeUnit.NANOSECONDS
          )
        }
        catch (e: InterruptedByTimeoutException) {}
      }
    }
  }

}
