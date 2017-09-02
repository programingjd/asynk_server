package info.jdavid.server

import kotlinx.coroutines.experimental.NonCancellable
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.run
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class Server internal constructor(address: InetSocketAddress, config: Config, cores: Int) {

  private val socketAcceptThread = singleThreadWithMaxPriority(address)
  private val socketAcceptDispatcher = socketAcceptThread.asCoroutineDispatcher()

  private val connectionHandleThreads = Executors.newScheduledThreadPool(cores * 2)
  private val connectionHandleDispatcher = connectionHandleThreads.asCoroutineDispatcher()

  private val socket = bind(address)

  fun stop() {
    socketAcceptLoop.cancel()
    try { socket.close() } catch (ignore: IOException) {}
    awaitTermination(socketAcceptThread)
  }

  private val socketAcceptLoop = {
    val sslContext = SSL.context(config.sslCertificate())
    val connectionHandler = config.connectionHandler
    val readTimeoutMillis = config.readTimeoutMillis
    val writeTimeoutMillis = config.writeTimeoutMillis
    val maxRequestSize = config.maxRequestSize
    val maxHeaderSize = config.maxHeaderSize

    println("Started listening on ${address.hostName}:${address.port}")

    val segmentPool = LockFreeLinkedListHead()
    val bufferPool = LockFreeLinkedListHead()

    val acceptedSockets = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)
    val closingSockets = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)

    launch(connectionHandleDispatcher) outer@ {

      while (true) {
        val clientChannel = acceptedSockets.receiveOrNull() ?: break

        launch(coroutineContext) inner@ {
          try {
            val clientAddress = clientChannel.remoteAddress as InetSocketAddress
            if (connectionHandler.reject(clientAddress)) return@inner

            val socketConnection = if (sslContext == null) {
              InsecureSocketConnection(clientChannel, segmentPool, maxRequestSize)
            }
            else {
              val sslParameters = connectionHandler.sslParameters(SSL.parameters())
              val sslEngine = SSL.engine(sslContext, sslParameters)
              SecureSocketConnection(clientChannel, segmentPool, maxRequestSize, sslEngine)
            }

            try {
              start(socketConnection, readTimeoutMillis, writeTimeoutMillis)
              val connection = connectionHandler.connect(
                coroutineContext, socketConnection, bufferPool,
                readTimeoutMillis, writeTimeoutMillis
              )
              try {
                while (!acceptedSockets.isClosedForSend) {
                  if (!connectionHandler.handle(socketConnection, clientAddress, connection,
                                                readTimeoutMillis, writeTimeoutMillis,
                                                maxHeaderSize)) {
                    break
                  }
                }
              }
              catch (ignore: IOException) {
                println("Connection closed prematurely")
              }
              catch (ignored: InterruptedByTimeoutException) {
                println("Timeout")
              }
              finally {
                connection.close()
                stop(socketConnection, readTimeoutMillis, writeTimeoutMillis)
              }
            }
            catch (ignored: InterruptedByTimeoutException) {
              println("Timeout")
            }
            finally {
              socketConnection.recycleBuffers()
            }

          }
          finally {
            closingSockets.send(clientChannel)
          }
        }

      }

    }

    var pending = 0
    val closeLoop = launch(socketAcceptDispatcher) {
      run(NonCancellable) {
        while (isActive || pending > 0 || !closingSockets.isEmpty) {
          val clientChannel = closingSockets.receiveOrNull() ?: break
          --pending
          try { clientChannel.close() } catch (ignore: IOException) {}
        }
        connectionHandleThreads.shutdownNow()
        awaitTermination(connectionHandleThreads)
        closingSockets.close()
        socketAcceptThread.shutdownNow()
      }
    }

    launch(socketAcceptDispatcher) {
      try {
        while (true) {
          val clientChannel = socket.aAccept()
          clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
          ++pending
          acceptedSockets.send(clientChannel)
        }
      }
      catch (ignore: CancellationException) {
        acceptedSockets.close()
        closeLoop.cancel()
        if (pending == 0) closingSockets.close()
      }
    }

  }()

  companion object {

    fun singleThreadWithMaxPriority(address: InetSocketAddress): ExecutorService {
      return Executors.newSingleThreadExecutor(
        @Suppress("ObjectLiteralToLambda") object: ThreadFactory {
          override fun newThread(r: Runnable): Thread {
            val t = Thread(null, r, "Socket ${address.hostName}:${address.port}")
            t.priority = Thread.MAX_PRIORITY
            return t
          }
        }
      )
    }

    fun bind(address: InetSocketAddress): AsynchronousServerSocketChannel {
      val serverChannel = AsynchronousServerSocketChannel.open()
      serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
      serverChannel.bind(address)
      return serverChannel
    }

    fun awaitTermination(threads: ExecutorService) {
      while (!threads.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
    }

    suspend private fun start(socketConnection: SocketConnection,
                                   readTimeoutMillis: Long, writeTimeoutMillis: Long) {
      val now = System.nanoTime()
      val readDeadline = now + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)
      val writeDeadline = now + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
      socketConnection.start(readDeadline, writeDeadline)
    }

    suspend private fun stop(socketConnection: SocketConnection,
                             readTimeoutMillis: Long, writeTimeoutMillis: Long) {
      val now = System.nanoTime()
      val readDeadline = now + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)
      val writeDeadline = now + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
      socketConnection.stop(readDeadline, writeDeadline)
    }

  }

}

fun main(args: Array<String>) {
  val server = Config().
    readTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
    writeTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
    certificate(java.io.File("localhost.p12")).port(8181).
//    port(8080).
    startServer()
  Thread.sleep(150000L)
  //server.stop()
  //Thread.sleep(15000L)
}
