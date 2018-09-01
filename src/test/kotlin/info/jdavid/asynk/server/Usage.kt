package info.jdavid.asynk.server

import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.handler.HttpHandler
import info.jdavid.asynk.server.http.route.NoParams
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

object Usage {

  @JvmStatic
  fun main(args: Array<String>) {
    echo()
  }

  fun start() {
    Server(
      HttpHandler.of(NoParams) { _, _, _, _ ->
        HttpHandler.StringResponse("Server 1", MediaType.TEXT)
      }
    )
    println("Server 1 started")

    Server.http(
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8081),
      HttpHandler.of(NoParams) { _, _, _, _ ->
        HttpHandler.StringResponse("Server 2", MediaType.TEXT)
      }
    )
    println ("Server 2 started")

    Server.http(
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8082),
      HttpHandler.Builder().
        route(NoParams).to { _, _, _, _ ->
        HttpHandler.StringResponse("Server 3", MediaType.TEXT)
      }.build()
    ).use {
      println ("Server 3 started")
    }
    println ("Server 3 stopped")
  }

  fun echo() {
    Server(
      object: Handler<Unit> {
        override suspend fun context(others: Collection<*>?) {}
        override suspend fun connect(remoteAddress: InetSocketAddress) = true
        override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Unit) {
          while (socket.aRead(buffer) != -1) {
            socket.aWrite(buffer.flip() as ByteBuffer)
            buffer.flip()
          }
        }
      },
      InetSocketAddress(InetAddress.getLoopbackAddress(), 7)
    )
  }

}
