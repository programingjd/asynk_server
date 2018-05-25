@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface Handler<CONTEXT> {

  suspend fun context(others: Collection<*>? = null): CONTEXT

  suspend fun connect(remoteAddress: InetSocketAddress): Boolean

  suspend fun handle(socket: AsynchronousSocketChannel,
                     buffer: ByteBuffer,
                     context: CONTEXT)

  open class Acceptance(val bodyAllowed: Boolean, val bodyRequired: Boolean)

}
