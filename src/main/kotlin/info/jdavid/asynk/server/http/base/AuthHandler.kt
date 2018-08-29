package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.handler.HttpHandler
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.Status
import info.jdavid.asynk.server.http.route.NoParams
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

abstract class AuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                           DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                           AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                           PARAMS: Any, VALIDATION_ERROR: AuthHandler.ValidationError>(
  @Suppress("MemberVisibilityCanBePrivate")
  protected val delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>
): HttpHandler<AuthHandler.Acceptance<ACCEPTANCE, PARAMS>, AUTH_CONTEXT, NoParams>(NoParams) {

  override suspend fun acceptUriInternal(method: Method,
                                         uri: String): AuthHandler.Acceptance<ACCEPTANCE, PARAMS>? {
    val acceptance = delegate.route.match(method, uri)?.let { delegate.acceptUri(method, uri, it) }
    return if (acceptance == null) null else Acceptance(acceptance)
  }

  final override suspend fun acceptUri(method: Method, uri: String, params: NoParams) = throw UnsupportedOperationException()

  final override suspend fun handle(acceptance: Acceptance<ACCEPTANCE, PARAMS>,
                                    headers: Headers,
                                    body: ByteBuffer,
                                    context: AUTH_CONTEXT): Response<*> {
    val error = validateCredentials(acceptance.delegate, headers, context)
    return if (error == null) {
      val response = delegate.handle(acceptance.delegate, headers, body, context.delegate)
      updateResponse(acceptance.delegate, headers, context, response)
      response
    }
    else {
      val response = unauthorizedResponse(acceptance.uri, headers)
      response.headers.set(
        Headers.WWW_AUTHENTICATE,
        wwwAuthenticate(acceptance.delegate, headers, error)
      )
      response
    }
  }

  protected open fun updateResponse(acceptance: ACCEPTANCE, headers: Headers, context: AUTH_CONTEXT,
                                    response: Response<*>) {
    val cacheControl = response.header(Headers.CACHE_CONTROL)
    val expires = response.header(Headers.EXPIRES)
    val cacheable =
      expires != null ||
      (cacheableMethod(
        acceptance.method) && cacheableStatusCode(
        response.statusCode))
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
  }

  protected open fun unauthorizedResponse(uri: String, headers: Headers): Response<*> {
    return UnauthorizedResponse()
  }

  abstract suspend fun validateCredentials(acceptance: ACCEPTANCE,
                                           headers: Headers,
                                           context: AUTH_CONTEXT): VALIDATION_ERROR?

  protected abstract fun wwwAuthenticate(acceptance: ACCEPTANCE,
                                         headers: Headers,
                                         error: VALIDATION_ERROR): String

  interface ValidationError

  class Acceptance<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>, PARAMS: Any>(
    internal val delegate: ACCEPTANCE
  ): HttpHandler.Acceptance<NoParams>(delegate.bodyAllowed, delegate.bodyRequired,
                                      delegate.method, delegate.uri, NoParams)

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
