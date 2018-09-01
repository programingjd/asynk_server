package info.jdavid.asynk.server

import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
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

  fun http() {
    Server(
      object: HttpHandler<HttpHandler.Acceptance<NoParams>, AbstractHttpHandler.Context, NoParams>(NoParams) {
        override suspend fun context(others: Collection<*>?) = Context(others)
        override suspend fun acceptUri(method: Method, uri: String, params: NoParams): Acceptance<NoParams>? {
          return when (method) {
            Method.HEAD, Method.GET -> Acceptance(false, false, method, uri, NoParams)
            else -> null
          }
        }
        override suspend fun handle(acceptance: Acceptance<NoParams>, headers: Headers, body: ByteBuffer,
                                    context: Context) = StringResponse(
          "Method: ${acceptance.method}\r\nUri: ${acceptance.uri}",
          MediaType.TEXT
        )
      }
    ).use {}
    Server(
      HttpHandler.of(NoParams) { acceptance, _, _, _ ->
        HttpHandler.StringResponse(
          "Method: ${acceptance.method}\r\nUri: ${acceptance.uri}",
          MediaType.TEXT
        )
      }
    ).use {}
  }

}
