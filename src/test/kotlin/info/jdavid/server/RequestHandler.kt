package info.jdavid.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.atomic.AtomicInteger


interface RequestHandler {

  suspend fun handle(channel: AsynchronousSocketChannel, address: InetSocketAddress,
                     readTimoutMillis: Long, writeTimeoutMillis: Long,
                     maxHeaderSize: Int,
                     segment: ByteBuffer, buffer: ByteBuffer)

  companion object {
    val counter = AtomicInteger()
    val DEFAULT = HttpRequestHandler()
  }

}
