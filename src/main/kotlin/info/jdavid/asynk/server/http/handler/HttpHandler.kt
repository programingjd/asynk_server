package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.FileRoute
import info.jdavid.asynk.server.http.route.FixedRoute
import info.jdavid.asynk.server.http.route.ParameterizedRoute
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.StandardOpenOption

/**
 * Route-based HTTP Handler.
 *
 * @param ACCEPTANCE the Acceptance object returned when accepting the connection. It carries information
 * about the request, such as its method and uri.
 * @param ACCEPTANCE_PARAMS the delegate acceptance object params type.
 * @param CONTEXT the thread-level context object that is shared by all instances of this handler
 * running on the same thread.
 * @param ROUTE_PARAMS the parameter type captured by the route when matching the request.
 * @param route the route that the handler serves.
 */
abstract class HttpHandler<ACCEPTANCE: HttpHandler.Acceptance<ACCEPTANCE_PARAMS>,
                           ACCEPTANCE_PARAMS: Any,
                           CONTEXT: AbstractHttpHandler.Context,
                           ROUTE_PARAMS: Any>(
  internal val route: Route<ROUTE_PARAMS>
): AbstractHttpHandler<ACCEPTANCE, CONTEXT>() {
  final override suspend fun acceptUri(method: Method, uri: String) = acceptUriInternal(method, uri)

  internal open suspend fun acceptUriInternal(method: Method, uri: String): ACCEPTANCE? {
    return route.match(method, uri)?.let { acceptUri(method, uri, it) }
  }

  /**
   * Returns whether this handler can handle an http request to the specified uri with the specified http
   * method by either returning null (it can't) or an acceptance object.
   * This validation is done after route matching and the parameters captured by the route matching are
   * also available for deciding whether to accept the request or not.
   * @param method the http method used for the request.
   * @param uri the http request uri.
   * @return the acceptance object, or null if the request is not accepted.
   */
  abstract suspend fun acceptUri(method: Method, uri: String, params: ROUTE_PARAMS): ACCEPTANCE?

  final override suspend fun handle(acceptance: ACCEPTANCE,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    socket: AsynchronousSocketChannel,
                                    context: CONTEXT) {
    val response = handle(acceptance, headers, body, context)
    decorateResponse(response)
    response.write(socket, body, acceptance.method)
  }

  /**
   * Updates the response before writing it to the socket.<br>
   * This is a good place to set a body on error pages for instance.
   * @param response the response object.
   */
  protected open fun decorateResponse(response: Response<*>) {
    response.header(Headers.CONNECTION, "close")
  }

  /**
   * Request handler method, responsible for returning an appropriate response object.
   * @param acceptance the acceptance object returned by [acceptUri].
   * @param headers the request headers.
   * @param body the request body as a ByteBuffer (may not contain any data). It can be reused to write
   * the response to the socket. The buffer size is maxRequestSize.
   * @param context the thread-level context object that is shared by all instances of this handler running on
   * the same thread.
   * @return the response.
   */
  abstract suspend fun handle(acceptance: ACCEPTANCE,
                              headers: Headers,
                              body: ByteBuffer,
                              context: CONTEXT): Response<*>

  /**
   * Abstract response object comprised of a status code, headers and an optional body.
   * @param statusCode the response status code.
   * @param body the return body or null if the response doesn't have a body.
   * @param headers the response headers (empty by default).
   * @param BODY the type of the body object.
   */
  abstract class Response<BODY>(val statusCode: Int,
                                protected var body: BODY? = null,
                                val headers: Headers = Headers()) {
    /**
     * Adds a header line with the specified header field name and value.
     * @param name the header field name.
     * @param value the header value.
     * @return this.
     */
    fun header(name: String, value: String): Response<BODY> {
      headers.add(name, value)
      return this
    }

    /**
     * Returns the header field value from the already added response header lines.
     * @param name the header field name.
     * @return the last header value or null if the header is missing.
     */
    fun header(name: String) = headers.value(name)

    /**
     * Adds a body to the response.
     * @param body the response body.
     * @return this.
     */
    fun body(body: BODY): Response<BODY> {
      this.body = body
      return this
    }

    /**
     * Specifies that this response doesn't have a body.
     * @return this.
     */
    fun noBody(): Response<BODY> {
      body = null
      return this
    }

    /**
     * Returns the body media type. If no media type is returned, then application/octet-stream will be used.
     * It is recommanded to use the predefined media types in [MediaType].
     * @param body the response body.
     * @return the media type, or null if it is not known.
     */
    protected abstract fun bodyMediaType(body: BODY): String?

    /**
     * Returns the length in bytes of the response body.
     * @param body the response body.
     * @return the byte size.
     */
    protected abstract suspend fun bodyByteLength(body: BODY): Long

    /**
     * Writes the body to the socket channel.
     * @param socket the socket channel to write to.
     * @param buffer a buffer (of maxRequestSize) that can be used to write to the socket more efficiently.
     */
    protected abstract suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer)

    private suspend fun error(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      buffer.put(ERROR_RESPONSE)
      buffer.flip()
      withTimeout(5000L) { socket.asyncWrite(buffer, true) }
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
      buffer.flip()
      withTimeout(5000L) { socket.asyncWrite(buffer, true) }
    }
    internal open suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer, method: Method) {
      val statusMessage = Status.HTTP_STATUSES[statusCode] ?: return error(socket, buffer.clear() as ByteBuffer)
      writeHeaders(socket, buffer, statusMessage)
      if (method != Method.HEAD) writeBody(socket, buffer)
    }
  }

  /**
   * [Response] implementation with a [File] body.
   * @param file the file that the response represents.
   * @param mediaType the file media type. [MediaType.fromFile] can be used if the media type is not known.
   * @param headers the response headers.
   * @param status the response status code.
   */
  class FileResponse(file: File,
                     private val mediaType: String,
                     headers: Headers = Headers(),
                     status: Int = Status.OK): Response<File>(
    status, file, headers
  ) {
    /**
     * @param file the file that the response represents.
     * @param mediaType the file media type. [MediaType.fromFile] can be used if the media type is not known.
     * @param status the response status code.
     */
    constructor(file: File, mediaType: String, status: Int): this(file, mediaType, Headers(), status)
    override fun bodyMediaType(body: File) = mediaType
    override suspend fun bodyByteLength(body: File) = body.length()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      body?.let {
        val channel = AsynchronousFileChannel.open(it.toPath(), StandardOpenOption.READ)
        var position = 0L
        while (true) {
          buffer.clear()
          val read = channel.asyncRead(buffer, position)
          if (read == -1L) break
          position += read
          buffer.flip()
          withTimeout(5000L) { socket.asyncWrite(buffer, true) }
        }
      }
    }
  }

  /**
   * [Response] implementation with a [String] body.
   */
  class StringResponse private constructor(body: ByteArray?,
                                           private val mediaType: String,
                                           headers: Headers = Headers(),
                                           status: Int): Response<ByteArray>(
    status, body, headers
  ) {
    /**
     * @param body the response body.
     * @param mediaType the file media type.
     * @param headers the response headers.
     * @param status the response status code.
     */
    constructor(body: String,
                mediaType: String = MediaType.TEXT,
                headers: Headers = Headers(),
                status: Int = Status.OK): this(
      body.toByteArray(), mediaType, headers, status
    )
    /**
     * @param body the response body.
     * @param mediaType the file media type.
     * @param status the response status code.
     */
    constructor(body: String,
                mediaType: String = MediaType.TEXT,
                status: Int = Status.OK): this(
      body.toByteArray(), mediaType, Headers(), status
    )
    /**
     * @param body the response body.
     * @param mediaType the file media type.
     * @param headers the response headers.
     * @param status the response status code.
     */
    constructor(body: CharSequence?,
                mediaType: String = MediaType.TEXT,
                headers: Headers = Headers(),
                status: Int = Status.OK): this(
      body?.toString()?.toByteArray(), mediaType, headers, status
    )
    /**
     * @param body the response body.
     * @param mediaType the file media type.
     * @param status the response status code.
     */
    constructor(body: CharSequence?,
                mediaType: String = MediaType.TEXT,
                status: Int = Status.OK): this(
      body?.toString()?.toByteArray(), mediaType, Headers(), status
    )
    /**
     * @param body the response body.
     * @param status the response status code.
     */
    constructor(body: CharSequence?,
                status: Int = Status.OK): this(
      body?.toString()?.toByteArray(), MediaType.TEXT, Headers(), status
    )
    override fun bodyMediaType(body: ByteArray) = mediaType
    override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      withTimeout(5000L) { socket.asyncWrite(ByteBuffer.wrap(body), true) }
    }
  }

  /**
   * [Response] implementation with a [ByteArray] body.
   * @param body the response body.
   * @param mediaType the file media type.
   * @param headers the response headers.
   * @param status the response status code.
   */
  class ByteResponse(body: ByteArray,
                     private val mediaType: String,
                     headers: Headers = Headers(),
                     status: Int = Status.OK): Response<ByteArray>(
    status,
    body,
    headers
  ) {

    /**
     * @param body the response body.
     * @param mediaType the file media type.
     * @param status the response status code.
     */
    constructor(body: ByteArray, mediaType: String, status: Int): this(
      body, mediaType, Headers(), status
    )
    override fun bodyMediaType(body: ByteArray) = mediaType
    override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      withTimeout(5000L) { socket.asyncWrite(ByteBuffer.wrap(body), true) }
    }
  }

  /**
   * [Response] implementation with no body.
   * @param headers the response headers.
   * @param status the response status code.
   */
  class EmptyResponse(headers: Headers = Headers(),
                      status: Int = Status.OK): Response<Nothing>(
    status, null, headers
  ) {
    /**
     * @param status the response status code.
     */
    constructor(status: Int): this(Headers(), status)
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }

  /**
   * Base class for HTTP Handlers acceptance objects. It stores the request method, uri and the captured
   * route parameters.
   * @param bodyAllowed specifies whether the request is allowed to include incoming data.
   * @param bodyRequired specifies whether the request body when allowed is required or not.
   * @param method the request method.
   * @param uri the request uri.
   * @param routeParams the captured route parameters.
   */
  open class Acceptance<out PARAMS>(bodyAllowed: Boolean,
                                    bodyRequired: Boolean,
                                    val method: Method,
                                    val uri: String,
                                    val routeParams: PARAMS): info.jdavid.asynk.server.http.Acceptance(
    bodyAllowed, bodyRequired
  )

  /**
   * A route is responsible for accepting or rejecting a request based on the its method and uri, and to
   * capture uri parameters from the request uri path.
   * @param PARAMS the type of the parameter object. If there is no parameter needed, then the
   * [info.jdavid.asynk.server.http.route.NoParams] object should be used.
   */
  interface Route<out PARAMS> {
    /**
     * Returns whether the route matches the specified request uri and method by either return null when it
     * doesn't match or by returning the captured parameters if it does.
     * @param method the request method.
     * @param uri the request uri.
     * @return the captured parameters or null if the route doesn't match.
     */
    fun match(method: Method, uri: String): PARAMS?
  }

  companion object {
    internal val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
    internal val ERROR_RESPONSE =
      ("HTTP/1.1 ${Status.INTERNAL_SERVER_ERROR} ${Status.HTTP_STATUSES[Status.INTERNAL_SERVER_ERROR]}\r\n" +
       "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").
        toByteArray(Charsets.US_ASCII)

//    /**
//     * Creates a new handler from a route and a handle function.
//     * @param route the handler route.
//     * @param handler a function that given the acceptance object, the request headers, the request body
//     *   and the context, returns a response object.
//     * @return the handler.
//     */
//    fun <PARAMS: Any> of(route: Route<PARAMS>,
//                         handler: (acceptance: HttpHandler.Acceptance<PARAMS>,
//                                   headers: Headers,
//                                   body: ByteBuffer,
//                                   context: AbstractHttpHandler.Context) -> Response<*>
//    ): HttpHandler<HttpHandler.Acceptance<PARAMS>, PARAMS, AbstractHttpHandler.Context, PARAMS> {
//      return object: HttpHandler<HttpHandler.Acceptance<PARAMS>,
//        PARAMS,
//        AbstractHttpHandler.Context,
//        PARAMS>(route) {
//        override suspend fun handle(acceptance: Acceptance<PARAMS>, headers: Headers, body: ByteBuffer,
//                                    context: Context) = handler.invoke(acceptance, headers, body, context)
//        override suspend fun context(others: Collection<*>?) = Context(others)
//        override suspend fun acceptUri(method: Method, uri: String, params: PARAMS) : Acceptance<PARAMS>? {
//          return when (method) {
//            Method.OPTIONS -> Acceptance(false, false, method, uri, params)
//            Method.HEAD -> Acceptance(false, false, method, uri, params)
//            Method.GET -> Acceptance(false, false, method, uri, params)
//            Method.POST -> Acceptance(true, true, method, uri, params)
//            Method.PUT -> Acceptance(true, true, method, uri, params)
//            Method.DELETE -> Acceptance(true, false, method, uri, params)
//            Method.PATCH -> Acceptance(true, true, method, uri, params)
//            else -> Acceptance(true, false, method, uri, params)
//          }
//        }
//      }
//    }

    /**
     * Creates a new handler from a route and a handle function.
     * @param route the handler route.
     * @param handler a function that given the acceptance object, the request headers, the request body
     *   and the context, returns a response object.
     * @return the handler.
     */
    fun <PARAMS: Any> of(route: Route<PARAMS>,
                         handler: suspend (acceptance: HttpHandler.Acceptance<PARAMS>,
                                           headers: Headers,
                                           body: ByteBuffer,
                                           context: AbstractHttpHandler.Context) -> Response<*>
    ): HttpHandler<HttpHandler.Acceptance<PARAMS>, PARAMS, AbstractHttpHandler.Context, PARAMS> {
      return object: HttpHandler<HttpHandler.Acceptance<PARAMS>,
                                 PARAMS,
                                 AbstractHttpHandler.Context,
                                 PARAMS>(route) {
        override suspend fun handle(acceptance: Acceptance<PARAMS>, headers: Headers, body: ByteBuffer,
                                    context: Context) = handler.invoke(acceptance, headers, body, context)
        override suspend fun context(others: Collection<*>?) = Context(others)
        override suspend fun acceptUri(method: Method, uri: String, params: PARAMS) : Acceptance<PARAMS>? {
          return when (method) {
            Method.OPTIONS -> Acceptance(false, false, method, uri, params)
            Method.HEAD -> Acceptance(false, false, method, uri, params)
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

  /**
   * Builder that can be used to combine multiple handlers (a chain) into a single handler.
   * The first handler that can accept the request will be used (first one wins).
   */
  class Builder {
    private val list = mutableListOf<HttpHandler<out HttpHandler.Acceptance<*>, *,
                                                 out AbstractHttpHandler.Context, *>>()

    /**
     * Specifies the route for the next handler.
     * @param route the next handler route.
     * @return a route definition that can be used to keep building the chain.
     */
    fun <PARAMS: Any> route(route: Route<PARAMS>) = RouteDefinition(route)

    /**
     * Specifies the route for the next handler as a [File] (root directory).
     * @param directory the next handler [FileRoute] root directory.
     * @return a route definition that can be used to keep building the chain.
     */
    fun route(directory: File) = RouteDefinition(FileRoute(directory))

    /**
     * Specifies the route for the next handler as a file path (for the root directory).
     * @param path the next handler [FileRoute] root directory file path.
     * @return a route definition that can be used to keep building the chain.
     */
    fun route(path: String): RouteDefinition<Map<String,String>> {
      if (path.isEmpty()) throw IllegalArgumentException("Path is empty.")
      return RouteDefinition(
        if (path[0] == '/' &&
            path.indexOf('{') != -1 && path.indexOf('}') != -1) ParameterizedRoute(path)
        else FixedRoute(path)
      )
    }

    /**
     * Specifies the next handler in the chain.
     * @param handler the next handler.
     * @return a handler definition that can be used to keep building the chain.
     */
    fun handler(handler: HttpHandler<out HttpHandler.Acceptance<*>, *, out AbstractHttpHandler.Context, *>) =
      HandlerDefinition(handler)

    inner class RouteDefinition<PARAMS: Any> internal constructor(private val route: Route<PARAMS>) {
//      /**
//       * Specifies the next handler in the chain.
//       * @param handler the next handler.
//       * @return a handler definition that can be used to keep building the chain.
//       */
//      fun to(handler: (acceptance: HttpHandler.Acceptance<PARAMS>,
//                       headers: Headers,
//                       body: ByteBuffer,
//                       context: AbstractHttpHandler.Context) -> Response<*>) =
//        HandlerDefinition(of(route, handler))
      /**
       * Specifies the next handler in the chain.
       * @param handler the next handler.
       * @return a handler definition that can be used to keep building the chain.
       */
      fun to(handler: suspend (acceptance: HttpHandler.Acceptance<PARAMS>,
                               headers: Headers,
                               body: ByteBuffer,
                               context: AbstractHttpHandler.Context) -> Response<*>) =
        HandlerDefinition(of(route, handler))
    }

    inner class HandlerDefinition internal constructor(
      private val handler: HttpHandler<*,*,*,*>) {

      /**
       * Specifies the route for the next handler.
       * @param route the next handler route.
       * @return a route definition that can be used to keep building the chain.
       */
      fun <PARAMS: Any> route(route: Route<PARAMS>): RouteDefinition<PARAMS> {
        list.add(handler)
        return this@Builder.route(route)
      }

      /**
       * Specifies the route for the next handler as a [File] (root directory).
       * @param directory the next handler [FileRoute] root directory.
       * @return a route definition that can be used to keep building the chain.
       */
      fun route(directory: File): RouteDefinition<File> {
        list.add(handler)
        return this@Builder.route(directory)
      }

      /**
       * Specifies the route for the next handler as a file path (for the root directory).
       * @param path the next handler [FileRoute] root directory file path.
       * @return a route definition that can be used to keep building the chain.
       */
      fun route(path: String): RouteDefinition<Map<String,String>> {
        list.add(handler)
        return this@Builder.route(path)
      }

      /**
       * Specifies the next handler in the chain.
       * @param handler the next handler.
       * @return a handler definition that can be used to keep building the chain.
       */
      fun handler(handler: HttpHandler<*,*,*,*>): HandlerDefinition {
        list.add(this.handler)
        return this@Builder.handler(handler)
      }

      /**
       * Closes the chain and returns the handler.
       * @return the handler.
       */
      fun build(): HttpHandler<*,*,*,*> {
        list.add(handler)
        return if (list.size == 1) list.first() else HttpHandlerChain(list)
      }
    }

  }

}
