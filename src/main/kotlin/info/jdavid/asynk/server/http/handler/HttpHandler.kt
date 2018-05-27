package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.Status
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.FileRoute
import info.jdavid.asynk.server.http.route.FixedRoute
import info.jdavid.asynk.server.http.route.NoParams
import info.jdavid.asynk.server.http.route.ParameterizedRoute
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

abstract class HttpHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                           CONTEXT: AbstractHttpHandler.Context,
                           PARAMS: Any>(
  val route: Route<PARAMS>
): AbstractHttpHandler<ACCEPTANCE, CONTEXT>() {
  final override suspend fun acceptUri(method: Method, uri: String): ACCEPTANCE? {
    return route.match(method, uri)?.let { acceptUri(method, uri, it) }
  }

  abstract suspend fun acceptUri(method: Method, uri: String, params: PARAMS): ACCEPTANCE?

  final override suspend fun handle(acceptance: ACCEPTANCE,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    socket: AsynchronousSocketChannel,
                                    context: CONTEXT) {
    val response = handle(acceptance, headers, body, context)
    response.header(Headers.CONNECTION, "close")
    response.write(socket, body, acceptance.method)
  }

  abstract suspend fun handle(acceptance: ACCEPTANCE,
                              headers: Headers,
                              body: ByteBuffer,
                              context: CONTEXT): Response<*>

  abstract class Response<BODY>(val statusCode: Int,
                                protected var body: BODY? = null,
                                val headers: Headers = Headers()) {
    fun header(name: String, value: String): Response<BODY> {
      headers.add(name, value)
      return this
    }
    fun header(name: String) = headers.value(name)
    fun body(body: BODY): Response<BODY> {
      this.body = body
      return this
    }
    fun noBody(): Response<BODY> {
      body = null
      return this
    }
    protected abstract fun bodyMediaType(body: BODY): String?
    protected abstract suspend fun bodyByteLength(body: BODY): Long
    protected abstract suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer)
    private suspend fun error(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      buffer.put(ERROR_RESPONSE)
      socket.aWrite(buffer.flip() as ByteBuffer, 5000L, TimeUnit.MILLISECONDS)
    }
    private suspend fun writeHeaders(socket: AsynchronousSocketChannel,
                                     buffer: ByteBuffer,
                                     statusMessage: String) {
      buffer.clear()
      body?.let {
        val contentLength = bodyByteLength(it)
        headers.set(Headers.CONTENT_LENGTH, contentLength.toString())
        headers.set(Headers.CONTENT_TYPE, bodyMediaType(it) ?: MediaType.OCTET_STREAM)
      }
      buffer.put("HTTP/1.1 ${statusCode} ${statusMessage}\r\n".toByteArray(Charsets.ISO_8859_1))
      for (line in headers.lines) {
        buffer.put(line.toByteArray(Charsets.ISO_8859_1))
        buffer.put(CRLF)
      }
      buffer.put(CRLF)
      socket.aWrite(buffer.flip() as ByteBuffer, 5000L, TimeUnit.MILLISECONDS)
    }
    internal open suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer, method: Method) {
      val statusMessage = Status.HTTP_STATUSES[statusCode] ?: return error(socket, buffer.clear() as ByteBuffer)
      writeHeaders(socket, buffer, statusMessage)
      if (method != Method.HEAD) writeBody(socket, buffer)
    }
  }

  class FileResponse(file: File?,
                     private val mediaType: String,
                     headers: Headers = Headers()): Response<File>(
    Status.OK, file, headers) {
    override fun bodyMediaType(body: File) = mediaType
    override suspend fun bodyByteLength(body: File) = body.length()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      body?.let {
        val channel = AsynchronousFileChannel.open(it.toPath(), StandardOpenOption.READ)
        var position = 0L
        while (true) {
          buffer.clear()
          val read = channel.aRead(buffer, position)
          if (read == -1) break
          position += read
          socket.aWrite(buffer.flip() as ByteBuffer, 5000, TimeUnit.MILLISECONDS)
        }
      }
    }
  }

  class StringResponse private constructor(body: ByteArray?,
                       private val mediaType: String,
                       headers: Headers = Headers()): Response<ByteArray>(
    Status.OK,
    body,
    headers) {
    constructor(body: String, mediaType: String, headers: Headers = Headers()): this(body.toByteArray(), mediaType, headers)
    constructor(body: CharSequence?, mediaType: String, headers: Headers = Headers()): this(body?.toString()?.toByteArray(), mediaType, headers)
    override fun bodyMediaType(body: ByteArray) = mediaType
    override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      socket.aWrite(ByteBuffer.wrap(body), 5000, TimeUnit.MILLISECONDS)
    }
  }

  class ByteResponse(body: ByteArray?,
                     private val mediaType: String,
                     headers: Headers = Headers()): Response<ByteArray>(
    Status.OK,
    body,
    headers) {
    override fun bodyMediaType(body: ByteArray) = mediaType
    override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      socket.aWrite(ByteBuffer.wrap(body), 5000, TimeUnit.MILLISECONDS)
    }
  }

  open class Acceptance<out PARAMS>(bodyAllowed: Boolean,
                                    bodyRequired: Boolean,
                                    val method: Method,
                                    val uri: String,
                                    val routeParams: PARAMS?): Handler.Acceptance(bodyAllowed, bodyRequired)

  interface Route<out PARAMS> {
    fun match(method: Method, uri: String): PARAMS?
  }

  companion object {
    val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
    val ERROR_RESPONSE =
      ("HTTP/1.1 ${Status.INTERNAL_SERVER_ERROR} ${Status.HTTP_STATUSES[Status.INTERNAL_SERVER_ERROR]}\r\n" +
       "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").
        toByteArray(Charsets.US_ASCII)
    fun <PARAMS: Any> of(route: Route<PARAMS>,
                         handler: (acceptance: HttpHandler.Acceptance<PARAMS>,
                                   headers: Headers,
                                   body: ByteBuffer,
                                   context: AbstractHttpHandler.Context) -> Response<*>
    ): HttpHandler<HttpHandler.Acceptance<PARAMS>, AbstractHttpHandler.Context, PARAMS> {
      return object: HttpHandler<HttpHandler.Acceptance<PARAMS>, AbstractHttpHandler.Context, PARAMS>(route) {
        override suspend fun handle(acceptance: Acceptance<PARAMS>, headers: Headers, body: ByteBuffer,
                                    context: Context) = handler.invoke(acceptance, headers, body, context)
        override suspend fun context(others: Collection<*>?) = Context(others)
        override suspend fun acceptUri(method: Method, uri: String, params: PARAMS) : Acceptance<PARAMS>? {
          return when (method) {
            Method.OPTIONS -> Acceptance(false, false, method, uri, params)
            Method.HEAD -> Acceptance(false,false, method, uri, params)
            Method.GET -> Acceptance(false, false, method, uri, params)
            Method.POST -> Acceptance(true, true, method, uri, params)
            Method.PUT -> Acceptance(true, true, method, uri, params)
            Method.DELETE -> Acceptance(true, false, method, uri, params)
            Method.PATCH -> Acceptance(true, true, method, uri, params)
            else -> Acceptance(true, false, method, uri, params)
          }
        }
      }
    }

  }

  class Builder {
    private val list = mutableListOf<AbstractHttpHandler<out Handler.Acceptance,
      out AbstractHttpHandler.Context>>()
    fun route() = Definition()
    fun <PARAMS: Any> route(route: Route<PARAMS>) = RouteDefinition(route)

    inner class Definition internal constructor() {
      fun matchAll() = RouteDefinition(NoParams)
      fun file(file: File) = RouteDefinition(FileRoute(file))
      fun fixed(path: String) = RouteDefinition(FixedRoute(path))
      fun parameterized(path: String) = RouteDefinition(ParameterizedRoute(path))
      fun handler(handler: AbstractHttpHandler<out Handler.Acceptance,
        out AbstractHttpHandler.Context>) =
        HandlerDefinition(handler)
    }

    inner class RouteDefinition<PARAMS: Any> internal constructor(private val route: Route<PARAMS>) {
      fun handle(handler: (acceptance: HttpHandler.Acceptance<PARAMS>,
                           headers: Headers,
                           body: ByteBuffer,
                           context: AbstractHttpHandler.Context) -> Response<*>) =
        HandlerDefinition(of(route, handler))
    }

    inner class HandlerDefinition internal constructor(
      private val handler: AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context>) {
      fun route(): Definition {
        list.add(handler)
        return Definition()
      }
      fun build(): AbstractHttpHandler<out Handler.Acceptance, out AbstractHttpHandler.Context> =
        if (list.size == 1) list.first() else HttpHandlerChain(list)
    }

  }

}
