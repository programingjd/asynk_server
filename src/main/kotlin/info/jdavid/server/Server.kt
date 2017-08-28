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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class Server internal constructor(address: InetSocketAddress,
                                  readTimeoutMillis: Long, writeTimeoutMillis: Long,
                                  maxHeaderSize: Int, maxRequestSize: Int,
                                  requestHandler: RequestHandler,
                                  cores: Int, cert: () -> ByteArray?) {
  @Suppress("ObjectLiteralToLambda")
  private val acceptThread = Executors.newSingleThreadExecutor(object: ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      val t = Thread(null, r, "Socket ${address.hostName}:${address.port}")
      t.priority = Thread.MAX_PRIORITY
      return t
    }
  })
  private val acceptDispatcher = acceptThread.asCoroutineDispatcher()
  private val serverChannel = openChannel(address)
  private val handleThreads = Executors.newScheduledThreadPool(cores * 2)
  private val handleDispatcher = handleThreads.asCoroutineDispatcher()

  private val acceptor = {
    val ssl = SSL.createSSLContext(cert())
    println("Started listening on ${address.hostName}:${address.port}")
    val nodes = LockFreeLinkedListHead()
    val accepted = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)
    val closing = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)
    launch(handleDispatcher) outer@ {
      while (true) {
        val clientChannel = accepted.receiveOrNull() ?: break
        launch(coroutineContext) inner@ {
          try {
            val clientAddress = clientChannel.remoteAddress as InetSocketAddress
            if (requestHandler.reject(clientAddress)) return@inner
            val channel = if (ssl == null) {
              InsecureChannel(clientChannel, nodes, maxRequestSize)
            }
            else {
              SecureChannel(clientChannel,
                            SSL.createSSLEngine(ssl, requestHandler.enableHttp2()),
                            nodes, maxRequestSize)
            }
            try {
              val start = System.nanoTime()
              channel.start(start + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                            start + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
              val connection = requestHandler.connection(coroutineContext,
                                                         channel, readTimeoutMillis, writeTimeoutMillis)
              try {
                while (!accepted.isClosedForSend) {
                  try {
                    val now = System.nanoTime()
                    if (!requestHandler.handle(channel, connection, clientAddress,
                                               now + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                                               now + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis),
                                               maxHeaderSize, channel.buffer())) {
                      break
                    }
                  }
                  finally {
                    channel.next()
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
                val stop = System.nanoTime()
                connection?.close()
                channel.stop(stop + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                             stop + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
              }
            }
            catch (ignored: InterruptedByTimeoutException) {
              println("Timeout")
            }
            finally {
              channel.recycle()
            }
          }
          finally {
            closing.send(clientChannel)
          }
        }
      }
    }
    var pending = 0
    val closer = launch(acceptDispatcher) {
      run(NonCancellable) {
        while (isActive || pending > 0 || !closing.isEmpty) {
          val clientChannel = closing.receiveOrNull() ?: break
          --pending
          try { clientChannel.close() } catch (ignore: IOException) {}
        }
        handleThreads.shutdownNow()
        while (!handleThreads.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
        closing.close()
        acceptThread.shutdownNow()
      }
    }
    launch(acceptDispatcher) {
      try {
        while (true) {
          val clientChannel = serverChannel.aAccept()
          ++pending
          accepted.send(clientChannel)
        }
      }
      catch (ignore: CancellationException) {
        accepted.close()
        closer.cancel()
        if (pending == 0) closing.close()
      }
    }
  }()

  companion object {
    fun openChannel(address: InetSocketAddress): AsynchronousServerSocketChannel {
      val serverChannel = AsynchronousServerSocketChannel.open()
      serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
      serverChannel.bind(address)
      return serverChannel
    }
//    val counter = AtomicInteger(0)
  }

  fun stop() {
    acceptor.cancel()
    try { serverChannel.close() } catch (ignore: IOException) {}
    while (!acceptThread.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
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
