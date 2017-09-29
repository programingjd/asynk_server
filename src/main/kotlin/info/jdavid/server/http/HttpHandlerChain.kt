package info.jdavid.server.http

import info.jdavid.server.Config
import info.jdavid.server.SecureSocketConnection
import info.jdavid.server.SocketConnection
import info.jdavid.server.http.handler.FileHandler
import info.jdavid.server.http.handler.Handler
import info.jdavid.server.http.http11.Headers
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.LinkedList


fun main(args: Array<String>) {
  Config().port(8080).requestHandler(
    HttpHandlerChain().add(FileHandler(File("i:/jdavid/ezpzjapanez.com/www")))
  ).startServer()
}

open class HttpHandlerChain(acmeHandler: Handler? = null): HttpConnectionHandler(false) {
  private val acmeHandler: Handler? = acmeHandler?.setup()
  private val chain = LinkedList<Handler>()

  fun add(handler: Handler): HttpHandlerChain {
    chain.add(handler.setup())
    return this
  }

  suspend final override fun handle(address: InetSocketAddress,
                                    method: String, uri: String, headers: Headers,
                                    socketConnection: SocketConnection,
                                    deadline: Long,
                                    buffer: ByteBuffer) {
    val secure = socketConnection is SecureSocketConnection
    val response = if (secure) {
      chain(method, uri, headers, socketConnection, deadline, buffer)
    }
    else if (isAcmeChallenge(method, uri, headers)) {
      val acmeHandlerParams = acmeHandler?.matches(method, uri)
      if (acmeHandlerParams == null) {
        Handler.Response(Statuses.NOT_FOUND)
      }
      else {
        acmeHandler!!.handle(method, uri, headers, socketConnection, deadline, buffer, acmeHandlerParams)
      }
    }
    else {
      if (allowInsecure(method, uri, headers)) {
        chain(method, uri, headers, socketConnection, deadline, buffer)
      }
      else {
        val host = headers.value("Host") ?: throw RuntimeException("Host header is missing.")
        val h = if (host[host.length - 1] == '/') host.substring(0, host.length - 1) else host
        Handler.Response(
          Statuses.PERMANENT_REDIRECT,
          Headers().
            add(Headers.LOCATION, Url.https("${h}${uri}")).
            add(CLOSE)
        )
      }
    }
    extraHeaders(response.headers)
    socketConnection.write(
      deadline,
      "HTTP/1.1 ${response.status} ${Statuses.message(response.status)}\r\n".toByteArray(Encodings.ASCII)
    )
    socketConnection.write(deadline, response.headers)
    if (response.writeBody != null) response.writeBody.invoke(socketConnection, buffer, deadline)
  }

  suspend private fun chain(method: String, uri: String, headers: Headers,
                            socketConnection: SocketConnection,
                            deadline: Long,
                            buffer: ByteBuffer): Handler.Response {
    for (handler in chain) {
      val params = handler.matches(method, uri)
      if (params != null) {
        return handler.handle(method, uri, headers, socketConnection, deadline, buffer, params)
      }
    }
    return Handler.Response(Statuses.NOT_FOUND)
  }

  open fun extraHeaders(headers: Headers) {}

  open fun isAcmeChallenge(method: String, uri: String, headers: Headers): Boolean {
    return method == "GET" && Uri.path(uri).startsWith("/.well-known/acme-challenge/")
  }

  open fun allowInsecure(method: String, uri: String, headers: Headers) = true

  suspend override fun reject(address: InetSocketAddress) = false

  override fun acceptUri(method: String, uri: String): Int {
    return super.acceptUri(method, uri)
  }

  override fun acceptHeaders(method: String, uri: String, headers: Headers): Int {
    return super.acceptHeaders(method, uri, headers)
  }

  override fun acceptBody(method: String): Int {
    return super.acceptBody(method)
  }

  private companion object {
    val EMPTY = "Content-Length: 0"
    val CLOSE = "Connection: close"
    val HSTS = "Strict-Transport-Security: max-age=31536000" //; includeSubDomain; preload"
  }

}
