package info.jdavid.asynk.server

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.http.*
import info.jdavid.asynk.server.http.base.DefaultHttpHandler
import info.jdavid.asynk.server.http.base.SimpleHttpHandler
import info.jdavid.asynk.server.http.handler.FileHandler
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

fun main(args: Array<String>) {
  var path = File(Server::class.java.protectionDomain.codeSource.location.path)
  while (true) {
    path = path.parentFile
    if (path.isDirectory && File(path, ".git").exists()) break
  }
  serveDirectory(path.resolve("src/test/resources"))
//  connectFor(60000L)
}

fun serveDirectory(directory: File) {
  FileHandler.serveDirectory(directory, 8080)
}

fun connectFor(millis: Long) {
  Server(object : DefaultHttpHandler() {
    override suspend fun connect(remoteAddress: InetSocketAddress) = true
    override suspend fun handle(acceptance: SimpleHttpHandler.Acceptance, headers: Headers, body: ByteBuffer,
                                socket: AsynchronousSocketChannel, context: Context) {
      when (Uri.path(acceptance.uri)) {
        "/headers", "/headers/" -> {
          val bytes =
            headers.lines.joinToString("\n", "", "\n").toByteArray(Charsets.US_ASCII)
          val size = bytes.size
          val type = "text/plain"
          val setup =
            "HTTP/1.1 200 OK\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate(bytes.size + setup.size).apply {
            put(setup)
            put(bytes)
            rewind()
          })
        }
        "/body", "/body/" -> {
          val size = body.remaining()
          val type = headers.value("Content-Type") ?: "text/plain"
          val setup =
            "HTTP/1.1 200 OK\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate((body.remaining()) + setup.size).apply {
            put(setup)
            if (body.remaining() > 0) put(body)
            rewind()
          })
        }
        else -> {
          val bytes = "NOT FOUND\n\nAvailable endpoints:\n/body\n/headers\n\n".toByteArray(Charsets.US_ASCII)
          val size = bytes.size
          val type = "text/plain"
          val setup =
            "HTTP/1.1 404 NOT FOUND\r\nContent-Type: ${type}\r\nContent-Length: ${size}\r\nConnection: close\r\n\r\n".
              toByteArray(Charsets.US_ASCII)
          socket.aWrite(ByteBuffer.allocate(bytes.size + setup.size).apply {
            put(setup)
            put(bytes)
            rewind()
          })
        }
      }

    }
  },/* SimpleHttpHandler(),*/ InetSocketAddress(InetAddress.getLoopbackAddress(), 8080), 4096).use {
    Thread.sleep(millis)
  }
}
