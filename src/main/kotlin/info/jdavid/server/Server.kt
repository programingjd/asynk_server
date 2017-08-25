package info.jdavid.server

import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
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
  private val looper = Executors.newSingleThreadExecutor(object: ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      val t = Thread(null, r, "Socket ${address.hostName}:${address.port}")
      t.priority = Thread.MAX_PRIORITY
      return t
    }
  })
  private val dispatcher = looper.asCoroutineDispatcher()
  private val serverChannel = openChannel(address)
  private val pool = Executors.newScheduledThreadPool(cores * 2)  //ForkJoinPool(cores)
  private val jobs = LockFreeLinkedListHead()
  private val job = launch(dispatcher) {
    val ssl = SSL.createSSLContext(cert())
    println("Started listening on ${address.hostName}:${address.port}")
    val nodes = LockFreeLinkedListHead()
    while (true) {
      try {
        println("accepting")
        val clientChannel = serverChannel.aAccept()
        println("accepted")
        val clientAddress = clientChannel.remoteAddress as InetSocketAddress
        if (requestHandler.reject(clientAddress)) {
          try {
            clientChannel.close()
          }
          catch (ignore: IOException) {}
          continue
        }
        val p = pool.asCoroutineDispatcher()
        launch(p) {
          val channel = if (ssl == null) {
            InsecureChannel(clientChannel, nodes, maxRequestSize)
          }
          else {
            SecureChannel(clientChannel, SSL.createSSLEngine(ssl, requestHandler.enableHttp2()),
                          nodes, maxRequestSize)
          }
          try {
            val async = async(p, CoroutineStart.LAZY) {
              try {
                val start = System.nanoTime()
                channel.start(start + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                              start + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
                val connection = requestHandler.connection(channel, readTimeoutMillis, writeTimeoutMillis)
                while (true) {
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
                val stop = System.nanoTime()
                connection?.close()
                channel.stop(stop + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                             stop + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
              }
              catch (ignore: IOException) {
                ignore.printStackTrace()
                println("Connection closed prematurely")
              }
              catch (ignored: InterruptedByTimeoutException) {
                println("Timeout")
              }
            }
            val node = Node(async)
            jobs.addLast(node)
            try {
              async.start()
              async.await()
            }
            finally {
              println("remove")
              node.remove()
            }
          }
          finally {
            println("closing")
            channel.recycle()
            launch(dispatcher) {
              try {
                println("close")
                clientChannel.close()
              }
              catch (ignore: IOException) {}
            }
          }
        }
      }
      catch (e: CancellationException) {
        println("canceled in")
        jobs.forEach<Node> {
          println("canceling")
          it.job.cancel()
        }
        pool.shutdownNow()
        while (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
        break
      }
    }
    println("Stopped listening on ${address.hostName}:${address.port}")
  }

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
    job.cancel()
    looper.shutdownNow()
    while (!looper.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
    try { serverChannel.close() } catch (ignore: IOException) {}
  }

  private class Node(val job: Job): LockFreeLinkedListNode()

}

fun main(args: Array<String>) {
  val server = Config().
    readTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
    writeTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
//    certificate(java.io.File("localhost.p12")).port(8181).
    port(8080).
    startServer()
  Thread.sleep(15000L)
  server.stop()
  Thread.sleep(50000L)
}
