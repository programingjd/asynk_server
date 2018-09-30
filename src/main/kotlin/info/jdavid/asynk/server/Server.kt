@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server

import info.jdavid.asynk.core.asyncAccept
import info.jdavid.asynk.server.http.handler.HttpHandler
import info.jdavid.asynk.server.http.handler.HttpHandlerChain
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.launch
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.coroutineContext as currentContext

/**
 * Generic TCP server implementation.
 * Requests are dispatched on a small thread pull (its size depends on the number
 * of cpu cores). Those threads are using coroutines to handle the requests.
 * Calling the constructor starts the server, and the [close] method stops it.
 * @param handler the handler responsible for accepting requests and sending responses.
 * @param address the address and port the server should bind to (defaults to localhost:8080).
 * @param maxRequestSize the maximum request size.
 * @param CONTEXT a context object stored by each dispatcher thread. Each thread calls [Handler.context]
 * and then shares the instance to all the request calls on that thread. This is used to share resources
 * (such as connection pools for instance). As long as [Handler.context] returns a new object instance,
 * then using thread safe objects or locks is unnecessary.
 */
open class Server<CONTEXT>(
  private val handler: Handler<CONTEXT>,
  private val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
  private val maxRequestSize: Int = 4096
): Closeable, CoroutineScope {
  private val logger = LoggerFactory.getLogger(Server::class.java)
  private val job = Job()
  override val coroutineContext: CoroutineContext = job
  private val connections = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)

  private val serverSocket: AsynchronousServerSocketChannel = AsynchronousServerSocketChannel.open().apply {
    setOption(StandardSocketOptions.SO_REUSEADDR, true)
    bind(address)
  }

  private val connectionAcceptor = Executors.newSingleThreadExecutor(ConnectionAcceptorThreadFactory())
  private val threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2)
  private val connectionHandlers = (1..threads).map {
    Executors.newSingleThreadExecutor(ConnectionHandlerThreadFactory(it))
  }

  private val acceptJob = launch(connectionAcceptor.asCoroutineDispatcher()) {
    try {
      while (isActive) {
        val clientSocket = serverSocket.asyncAccept()
        clientSocket.setOption(StandardSocketOptions.TCP_NODELAY, true)
        connections.send(clientSocket)
      }
    }
    catch (e: CancellationException) {}
    catch (e: IOException) {
      logger.warn("Acceptor error", e)
    }
  }

  private val handleJobs = connectionHandlers.map {
    launch(it.asCoroutineDispatcher()) {
      val handlerContext = handler.context()
      val buffers = LinkedList<ByteBuffer>()
      try {
        while (isActive) {
          val clientSocket = connections.receiveOrNull() ?: break
          val remoteAddress = clientSocket.remoteAddress as InetSocketAddress
          launch(currentContext) {
            if (handler.connect(remoteAddress)) {
              val buffer = buffers.poll() ?: ByteBuffer.allocateDirect(maxRequestSize)
              try {
                handler.handle(clientSocket, buffer, handlerContext)
              }
              catch (e: Exception) {
                logger.warn("Handler error", e)
              }
              finally {
                buffers.offer(buffer)
              }
            }
            //delay(3000L)
            clientSocket.close()
          }
        }
      }
      catch (e: CancellationException) {}
      catch (e: IOException) {
        logger.warn("Dispatcher error", e)
      }
    }
  }

  override fun close() {
    acceptJob.cancel()
    connectionAcceptor.shutdownNow()
    connectionAcceptor.awaitTermination(5000L, TimeUnit.MILLISECONDS)
    handleJobs.forEach { it.cancel() }
    connectionHandlers.forEach { it.shutdown() }
    val deadline = System.currentTimeMillis() + 5000L
    connectionHandlers.forEach {
      it.awaitTermination(Math.max(250L, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
    }
    serverSocket.close()
    job.cancel()
  }

  private inner class ConnectionAcceptorThreadFactory: ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      return Thread(r, "Acceptor ${address.hostName}:${address.port}").apply {
        priority = Thread.MAX_PRIORITY
      }
    }
  }

  private inner class ConnectionHandlerThreadFactory(val n: Int): ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      return Thread(r, "Handler ${n} ${address.hostName}:${address.port}").apply {
        priority = Thread.MAX_PRIORITY
      }
    }
  }

  companion object {
    /**
     * Starts an http server on the specified address using the default port (8080).
     * @param address the server address.
     * @param maxRequestSize the max request size.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(address: InetSocketAddress, maxRequestSize: Int, vararg chain: HttpHandler<*,*,*,*>):
      Server<*> = Server(HttpHandlerChain(chain.toList()), address, maxRequestSize)
    /**
     * Starts an http server on the specified address using the default port (8080).
     * @param address the server address.
     * @param maxRequestSize the max request size.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(address: InetSocketAddress, maxRequestSize: Int, chain: List<HttpHandler<*,*,*,*>>):
      Server<*> = Server(HttpHandlerChain(chain.toList()), address, maxRequestSize)
    /**
     * Starts an http server on the specified address using using the default port and with the
     * default max request size of 4kb.
     * @param address the server address.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(address: InetSocketAddress, vararg chain: HttpHandler<*,*,*,*>) =
      http(address, 4096, *chain)
    /**
     * Starts an http server on the specified address using using the default port and with the
     * default max request size of 4kb.
     * @param address the server address.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(address: InetSocketAddress, chain: List<HttpHandler<*,*,*,*>>) =
      http(address, 4096, chain)
    /**
     * Starts an http server on  localhost:8080.
     * @param maxRequestSize the max request size.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(maxRequestSize: Int, vararg chain: HttpHandler<*,*,*,*>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), maxRequestSize, *chain)
    /**
     * Starts an http server on  localhost:8080.
     * @param maxRequestSize the max request size.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(maxRequestSize: Int, chain: List<HttpHandler<*,*,*,*>>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), maxRequestSize, chain)
    /**
     * Starts an http server on  localhost:8080 with the default max request size of 4kb.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(vararg chain: HttpHandler<*,*,*,*>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096, *chain)
    /**
     * Starts an http server on  localhost:8080 with the default max request size of 4kb.
     * @param chain an ordered list of http handlers (the first one that accepts the request wins).
     * @return the server instance.
     */
    fun http(chain: List<HttpHandler<*,*,*,*>>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096, chain)
  }

}
