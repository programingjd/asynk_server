package info.jdavid.asynk.server.samples

import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.Server
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

class EchoServer {

  fun test() {
    Server(object: Handler<Nothing?> {
      override suspend fun context(others: Collection<*>?) = null
      override suspend fun connect(remoteAddress: InetSocketAddress) = true
      override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Nothing?) {
        while (socket.aRead(buffer, 5, TimeUnit.SECONDS) > -1) {
          socket.aWrite(buffer, 5, TimeUnit.SECONDS)
        }
      }
    }).use {
      Thread.sleep(10000L)
    }
  }

}
