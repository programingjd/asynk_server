@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server

import info.jdavid.server.http.base.AbstractHttpHandler
import info.jdavid.server.http.handler.HttpHandlerChain
import kotlinx.coroutines.experimental.JobCancellationException
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
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

open class Server<CONTEXT>(
  private val handler: Handler<CONTEXT>,
  private val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
  private val maxRequestSize: Int = 4096
): Closeable {
  private val logger = LoggerFactory.getLogger(Server::class.java)

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
        val clientSocket = serverSocket.aAccept()
        clientSocket.setOption(StandardSocketOptions.TCP_NODELAY, true)
        connections.send(clientSocket)
      }
    }
    catch (e: JobCancellationException) {}
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
          launch(coroutineContext) {
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
      catch (e: JobCancellationException) {}
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
    fun http(address: InetSocketAddress,
             maxRequestSize: Int,
             vararg chain: AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>):
      Server<*> = Server(HttpHandlerChain(chain.toList()), address, maxRequestSize)
    fun http(vararg chain: AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096, *chain)
    fun http(address: InetSocketAddress,
             vararg chain: AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>) =
      http(address, 4096, *chain)
    fun http(maxRequestSize: Int,
             vararg chain: AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), maxRequestSize, *chain)
    fun http(address: InetSocketAddress,
             maxRequestSize: Int,
             chain: List<AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>>):
      Server<*> = Server(HttpHandlerChain(chain.toList()), address, maxRequestSize)
    fun http(chain: List<AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096, chain)
    fun http(address: InetSocketAddress,
             chain: List<AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>>) =
      http(address, 4096, chain)
    fun http(maxRequestSize: Int,
             chain: List<AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>>) =
      http(InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), maxRequestSize, chain)
  }

}
