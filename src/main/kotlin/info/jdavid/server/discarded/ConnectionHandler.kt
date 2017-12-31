package info.jdavid.server.discarded

import info.jdavid.server.discarded.http.Encodings
import info.jdavid.server.discarded.http.http11.Headers
import info.jdavid.server.discarded.http.HttpConnectionHandler
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLParameters
import kotlin.coroutines.experimental.CoroutineContext

interface ConnectionHandler {

  suspend fun reject(address: InetSocketAddress): Boolean

  fun sslParameters(defaultSSLParameters: SSLParameters): SSLParameters

  suspend fun connect(context: CoroutineContext,
                      socketConnection: SocketConnection,
                      bufferPool: LockFreeLinkedListHead,
                      readTimeoutMillis: Long, writeTimeoutMillis: Long,
                      maxRequestSize: Int): Connection

  suspend fun handle(socketConnection: SocketConnection, address: InetSocketAddress, connection: Connection,
                     readTimeoutMillis: Long, writeTimeoutMillis: Long,
                     maxHeaderSize: Int): Boolean

  companion object {
    val DEFAULT = object: HttpConnectionHandler(true) {
      suspend override fun reject(address: InetSocketAddress): Boolean {
        println("Client: ${address.address}")
        return false
      }
      override fun acceptUri(method: String, uri: String): Int {
        println("Method: ${method}")
        println("Url: ${uri}")
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
                                  socketConnection: SocketConnection,
                                  deadline: Long,
                                  buffer: ByteBuffer) {
        buffer.rewind().limit(buffer.capacity())
        buffer.put("HTTP/1.1 200 OK\r\n".toByteArray(Encodings.ASCII))
        socketConnection.write(deadline, buffer)
        val h = Headers()
        h.add(Headers.CONTENT_TYPE, "text/plain; charset=utf-8")
        h.add(Headers.CONTENT_LENGTH, "6")
        socketConnection.write(deadline, h)
        buffer.put("body\n".toByteArray(Encodings.UTF_8))
        socketConnection.write(deadline, buffer)
        delay(5, TimeUnit.SECONDS)
        buffer.put("\n".toByteArray(Encodings.UTF_8))
        socketConnection.write(deadline, buffer)
      }
    }
  }

}
