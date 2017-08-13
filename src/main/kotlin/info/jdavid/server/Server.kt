package info.jdavid.server

import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.launch
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class Server internal constructor(address: InetSocketAddress,
                                  readTimeoutMillis: Long, writeTimeoutMillis: Long,
                                  maxHeaderSize: Int, maxRequestSize: Int,
                                  requestHandler: RequestHandler,
                                  cores: Int) {
  @Suppress("ObjectLiteralToLambda")
  private val looper = Executors.newSingleThreadExecutor(object: ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      val t = Thread(null, r, "Socket ${address.hostName}:${address.port}")
      t.priority = Thread.MAX_PRIORITY
      return t
    }
  })
  private val serverChannel = openChannel(address)
  private val job = launch(looper.asCoroutineDispatcher()) {
    val pool = ForkJoinPool(cores)
    val dispatcher = pool.asCoroutineDispatcher()
    val channel = serverChannel
    println("Started listening on ${address.hostName}:${address.port}")
    val nodes = LockFreeLinkedListHead()
    while (true) {
      try {
        val clientChannel = channel.accept().get()
        launch(dispatcher) {
          val node = nodes.removeFirstOrNull() as? Node ?: Node(8192, maxRequestSize)
          val clientAddress = clientChannel.remoteAddress as InetSocketAddress
          try {
            requestHandler.handle(clientChannel, clientAddress,
                                  readTimeoutMillis, writeTimeoutMillis,
                                  maxHeaderSize, node.segment, node.buffer)
          }
          finally {
            node.segment.rewind()
            node.buffer.rewind()
            nodes.addLast(node)
          }
        }
      } catch (e: InterruptedException) {
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
  }


  private class Node(segmentSize: Int, bufferSize: Int): LockFreeLinkedListNode() {
    internal val segment: ByteBuffer = ByteBuffer.allocate(segmentSize)
    internal val buffer: ByteBuffer = ByteBuffer.allocate(bufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}

fun main(args: Array<String>) {
  val server = Config().startServer()
  //Thread.sleep(15000L)
  //server.stop()
}
