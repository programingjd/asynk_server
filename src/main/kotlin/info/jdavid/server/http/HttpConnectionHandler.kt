package info.jdavid.server.http

import info.jdavid.server.SocketConnection
import info.jdavid.server.Connection
import info.jdavid.server.ConnectionHandler
import info.jdavid.server.SecureSocketConnection
import info.jdavid.server.http.http11.Headers
import info.jdavid.server.http.http11.Http11Connection
import info.jdavid.server.http.http2.Http2Connection
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.SSLParameters
import kotlin.coroutines.experimental.CoroutineContext

abstract class HttpConnectionHandler(enableHttp2: Boolean): ConnectionHandler {

  private val protocols = if (enableHttp2) arrayOf("h2", "http/1.1") else arrayOf("http/1.1")

  suspend abstract fun handle(address: InetSocketAddress,
                              method: String,
                              uri: String,
                              headers: Headers,
                              socketConnection: SocketConnection,
                              deadline: Long,
                              buffer: ByteBuffer)

  override fun sslParameters(defaultSSLParameters: SSLParameters): SSLParameters {
    defaultSSLParameters.applicationProtocols = protocols
    return defaultSSLParameters
  }


  // accept methods return values:
  //  -1       -> accept
  //   0       -> drop connection
  //  100..500 -> error code

  open fun acceptUri(method: String, uri: String): Int {
    return if (method == "GET" || method == "HEAD") -1 else 404
  }

  open fun acceptHeaders(method: String, uri: String, headers: Headers): Int {
    return if (headers.has("host")) -1 else 0
  }

  open fun acceptBody(method: String): Int = -1

  suspend override fun connect(context: CoroutineContext, socketConnection: SocketConnection,
                               bufferPool: LockFreeLinkedListHead,
                               readTimeoutMillis: Long, writeTimeoutMillis: Long,
                               maxRequestSize: Int): Connection {
    return if (socketConnection is SecureSocketConnection &&
               socketConnection.applicationProtocol() == "h2") {
      Http2Connection(
        bufferPool, maxRequestSize,
        context, socketConnection,
        readTimeoutMillis, writeTimeoutMillis
      ).start()
    }
    else {
      Http11Connection(bufferPool, maxRequestSize)
    }
  }

  suspend final override fun handle(socketConnection: SocketConnection,
                                    address: InetSocketAddress,
                                    connection: Connection,
                                    readTimeoutMillis: Long, writeTimeoutMillis: Long,
                                    maxHeaderSize: Int): Boolean {
    return when (connection) {
      is Http2Connection -> {
        connection.stream(readTimeoutMillis, writeTimeoutMillis)
        false
  //        { http2(socketConnection, address, readTimeoutMillis, writeTimeoutMillis, maxHeaderSize, buffer) }
  //      )
      }
      is Http11Connection -> {
        val buffers = connection.buffers()
        try {
          connection.handle(this,
                            socketConnection,
                            address,
                            readTimeoutMillis, writeTimeoutMillis,
                            maxHeaderSize,
                            buffers.buffer)
        }
        finally {
          connection.recycle(buffers)
        }
      }
      else -> throw RuntimeException()
    }
  }

}
