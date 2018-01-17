@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface Handler<C> {

  fun context(): C

  suspend fun connect(remoteAddress: InetSocketAddress): Boolean

  suspend fun handle(socket: AsynchronousSocketChannel,
                     buffer: ByteBuffer,
                     context: C)

  open class Acceptance(val bodyAllowed: Boolean, val bodyRequired: Boolean)

}
