package info.jdavid.server.http

import java.nio.ByteBuffer

abstract class AuthHandler<A: HttpHandler.Acceptance,
                           C: AbstractHttpHandler.Context,
                           D: AuthHandler.Context<C>>(
  protected val delegate: HttpHandler<A, C>
): HttpHandler<A, D>() {

  final override suspend fun acceptUri(method: Method, uri: String): A? {
    return delegate.acceptUri(method, uri)
  }

  final override suspend fun handle(acceptance: A,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    context: D): Response<*> {
    if (credentialsAreValid(acceptance, headers, context)) {
      val response = delegate.handle(acceptance, headers, body, context.delegate)
      val cacheControl = response.header(Headers.CACHE_CONTROL)
      val expires = response.header(Headers.EXPIRES)
      val cacheable =
        expires != null ||
        (cacheableMethod(acceptance.method) && cacheableStatusCode(response.statusCode))
      if (cacheControl == null) {
        if (cacheable) response.header(Headers.CACHE_CONTROL, PRIVATE)
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
      return UnauthorizedResponse().header(Headers.WWW_AUTHENTICATE, wwwAuthenticate())
    }
  }

  abstract suspend fun credentialsAreValid(acceptance: A, headers: Headers, context: D): Boolean

  abstract fun wwwAuthenticate(): String

  open class Context<C>(val delegate: C): AbstractHttpHandler.Context()

  class UnauthorizedResponse: Response<Nothing>(Status.UNAUTHORIZED) {
    override fun bodyMediaType() = null
    override suspend fun bodyByteLength() = 0L
    override suspend fun writeBody(buffer: ByteBuffer) {}
  }

  internal companion object {
    fun cacheableMethod(method: Method): Boolean {
      return method == Method.GET || method == Method.HEAD
    }
    fun cacheableStatusCode(code: Int): Boolean {
      return code == 200 || code == 206 || code == 300 || code == 301 || code == 308
    }
    val NO_STORE = "no-store"
    val PRIVATE = "private"
    val PUBLIC = "public"
    val MAX_AGE_EQUALS = "max-age="
    val S_MAX_AGE_EQUALS = "s-maxage="
    val STALE_WHILE_REVALIDATE_EQUALS = "stale-while-revalite="
    val STALE_IF_ERROR_EQUALS = "stale-if-error="
  }

}
