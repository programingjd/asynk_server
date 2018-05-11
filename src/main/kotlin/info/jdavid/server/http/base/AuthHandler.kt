package info.jdavid.server.http.base

import info.jdavid.server.http.Headers
import info.jdavid.server.http.handler.HttpHandler
import info.jdavid.server.http.Method
import info.jdavid.server.http.Status
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

abstract class AuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                           DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                           AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                           PARAMS: Any>(
  @Suppress("MemberVisibilityCanBePrivate")
  protected val delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>
): HttpHandler<ACCEPTANCE, AUTH_CONTEXT, PARAMS>(delegate.route) {

  final override suspend fun acceptUri(method: Method, uri: String, params: PARAMS): ACCEPTANCE? {
    return delegate.acceptUri(method, uri, params)
  }

  final override suspend fun handle(acceptance: ACCEPTANCE,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    context: AUTH_CONTEXT): Response<*> {
    if (credentialsAreValid(acceptance, headers, context)) {
      val response = delegate.handle(acceptance, headers, body, context.delegate)
      val cacheControl = response.header(Headers.CACHE_CONTROL)
      val expires = response.header(Headers.EXPIRES)
      val cacheable =
        expires != null ||
        (cacheableMethod(
          acceptance.method) && cacheableStatusCode(
          response.statusCode))
      if (cacheControl == null) {
        if (cacheable) response.header(Headers.CACHE_CONTROL,
                                       PRIVATE)
      }
      else {
        loop@ for (directive in cacheControl.split(',')) {
          when (directive) {
            NO_STORE, PRIVATE, PUBLIC -> break@loop
          }
          if (!cacheable) {
            val i = directive.indexOf('=')
            val d = if (i == -1) directive else directive.substring(0, i + 1)
            when (d) {
              MAX_AGE_EQUALS, S_MAX_AGE_EQUALS, STALE_WHILE_REVALIDATE_EQUALS, STALE_IF_ERROR_EQUALS -> {
                response.header(Headers.CACHE_CONTROL, "private, ${cacheControl}")
              }
            }
          }
          else response.header(Headers.CACHE_CONTROL, "private, ${cacheControl}")
        }
      }
      return response
    }
    else {
      return UnauthorizedResponse().header(
        Headers.WWW_AUTHENTICATE,
        wwwAuthenticate(acceptance, headers)
      )
    }
  }

  abstract suspend fun credentialsAreValid(acceptance: ACCEPTANCE,
                                           headers: Headers,
                                           context: AUTH_CONTEXT): Boolean

  protected abstract fun wwwAuthenticate(acceptance: ACCEPTANCE,
                                         headers: Headers): String

  open class Context<out CONTEXT>(others: Collection<*>?,
                                  val delegate: CONTEXT): AbstractHttpHandler.Context(others)

  class UnauthorizedResponse: Response<Nothing>(
    Status.UNAUTHORIZED) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }

  internal companion object {
    fun cacheableMethod(method: Method): Boolean {
      return method == Method.GET || method == Method.HEAD
    }
    fun cacheableStatusCode(code: Int): Boolean {
      return code == 200 || code == 206 || code == 300 || code == 301 || code == 308
    }
    const val NO_STORE = "no-store"
    const val PRIVATE = "private"
    const val PUBLIC = "public"
    const val MAX_AGE_EQUALS = "max-age="
    const val S_MAX_AGE_EQUALS = "s-maxage="
    const val STALE_WHILE_REVALIDATE_EQUALS = "stale-while-revalite="
    const val STALE_IF_ERROR_EQUALS = "stale-if-error="
  }

}
