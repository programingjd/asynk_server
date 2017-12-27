package info.jdavid.server.dev

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface Handler {

  suspend fun context(): Any?

  suspend fun connect(remoteAddress: InetSocketAddress): Boolean

  suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Any?)

}
