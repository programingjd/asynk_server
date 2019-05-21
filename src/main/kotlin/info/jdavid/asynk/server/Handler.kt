@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

/**
 * Request handler interface.
 * @param CONTEXT the thread-level context object shared by all the handlers running on the same thread.
 */
interface Handler<CONTEXT> {

  /**
   * Returns the thread-level context. The same instance will be shared for all calls to [handle] on the same
   * thread, but only on the same thread. Therefore, if a new object instance is returned, then access to
   * the object can be done without synchronization or locking, and non-thread-safe structures can be used.
   * @param others the other contexts in the same handler chain for the same thread. This can be used
   * to share expensive objects between handlers.
   * @return the context.
   */
  suspend fun context(others: Collection<*>? = null): CONTEXT

  /**
   * Accepts or rejects a connection from a specific remote address. This can be used to only allow
   * connections from specific addresses, or to implement a blacklist.
   * @param remoteAddress the address of the incoming connection.
   * @return true to accept, false to reject.
   */
  suspend fun connect(remoteAddress: InetSocketAddress): Boolean

  /**
   * Handles an incoming request on the specified socket. This method is responsible for reading the incoming
   * request data, for writing the outgoing response data, but not for closing the socket channel.
   * @param socket the socket channel to read from and write to.
   * @param remoteAddress the address of the incoming connection.
   * @param buffer a buffer of max request size that is recycled by the dispatcher.
   * @param context the context object shared by all the [handle] calls on this thread only.
   */
  suspend fun handle(socket: AsynchronousSocketChannel,
                     remoteAddress: InetSocketAddress,
                     buffer: ByteBuffer,
                     context: CONTEXT)

}
