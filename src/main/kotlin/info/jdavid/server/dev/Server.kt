package info.jdavid.server.dev

import kotlinx.coroutines.experimental.JobCancellationException
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

public class Server(
  private val address: InetSocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 8080)
) {

  private val connections = Channel<AsynchronousSocketChannel>(Channel.UNLIMITED)

  private val serverSocket: AsynchronousServerSocketChannel = AsynchronousServerSocketChannel.open().apply {
    setOption(StandardSocketOptions.SO_REUSEADDR, true)
    bind(address)
  }

  private val connectionAcceptor = Executors.newSingleThreadExecutor(ConnectionAcceptorThreadFactory())
  private val connectionHandlers = (1..4).map {
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
      e.printStackTrace()
    }
  }

  private val OK =
    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nTest".
      toByteArray(Charsets.US_ASCII)

  private val handleJobs = connectionHandlers.map {
    launch(it.asCoroutineDispatcher()) {
      val output = ByteBuffer.allocateDirect(OK.size).apply {
        put(OK)
      }
      val input = ByteBuffer.allocateDirect(4096)
      try {
        while (isActive) {
          val clientSocket = connections.receiveOrNull() ?: break
          clientSocket.aRead(input.rewind() as ByteBuffer)
          clientSocket.aWrite(output.rewind() as ByteBuffer)
          clientSocket.close()
        }
      }
      catch (e: JobCancellationException) {}
      catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }

  fun stop() {
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

}
