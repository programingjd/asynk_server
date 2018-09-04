package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import info.jdavid.asynk.server.http.handler.HttpHandler
import info.jdavid.asynk.server.http.route.NoParams
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

/**
 * Abstract HTTP Authentication handler. It validates the Authentication header value, and either
 * returns a WWW-Authenticate with the supported authentication methods if it isn't valid, or forwards
 * the handling to another handler.
 * @param delegate the delegate handler that should handle accepted requests with valid authentication.
 * @param ACCEPTANCE the delegate acceptance object type.
 * @param ACCEPTANCE_PARAMS the delegate acceptance object params type.
 * @param DELEGATE_CONTEXT the delegate context object type.
 * @param AUTH_CONTEXT the authentication context object type that wraps the delegate context. It can be used
 * to carry extra information used to validate credentials (a list of revoked tokens for instance).
 * @param ROUTE_PARAMS the parameter type captured by the route when matching the request.
 * @param VALIDATION_ERROR an error type that should include any information necessary to generate the
 * appropriate WWW-Authenticate header.
 */
abstract class AuthHandler<ACCEPTANCE: HttpHandler.Acceptance<ACCEPTANCE_PARAMS>,
                           ACCEPTANCE_PARAMS: Any,
                           DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                           AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                           ROUTE_PARAMS: Any, VALIDATION_ERROR: AuthHandler.ValidationError>(
  @Suppress("MemberVisibilityCanBePrivate")
  protected val delegate: HttpHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, DELEGATE_CONTEXT, ROUTE_PARAMS>
): HttpHandler<AuthHandler.Acceptance<ACCEPTANCE>, NoParams, AUTH_CONTEXT, NoParams>(NoParams) {

  override suspend fun acceptUriInternal(method: Method,
                                         uri: String): AuthHandler.Acceptance<ACCEPTANCE>? {
    val acceptance = delegate.route.match(method, uri)?.let { delegate.acceptUri(method, uri, it) }
    return if (acceptance == null) null else Acceptance(acceptance)
  }

  final override suspend fun acceptUri(method: Method, uri: String, params: NoParams) = throw UnsupportedOperationException()

  final override suspend fun handle(acceptance: Acceptance<ACCEPTANCE>,
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

  /**
   * Modifies the response of the delegate handler. By default, it changes the Cache-Control policy to
   * private.
   * @param acceptance the acceptance object returned by the delegate handler when accepting the request.
   * @param headers the request headers.
   * @param context the thread-level context object shared by all instances of this handler running on the
   * same thread.
   * @param response the response sent by the delegate handler.
   */
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

  /**
   * Creates the response for unauthorized requests (WWW-Authenticate header is added later).
   * @param uri the request uri.
   * @param headers the request headers.
   */
  protected open fun unauthorizedResponse(uri: String, headers: Headers): Response<*> {
    return UnauthorizedResponse()
  }

  /**
   * Checks for authentication errors by validating the credentials. It either returns null if the credentials
   * are correct, or the error if they aren't.
   * @param acceptance the acceptance object returned by the delegate handler when accepting the request.
   * @param headers the request headers.
   * @param context the thread-level context object shared by all instances of this handler running on the
   * same thread.
   * @return an error or null if the credentials are valid.
   */
  abstract suspend fun validateCredentials(acceptance: ACCEPTANCE,
                                           headers: Headers,
                                           context: AUTH_CONTEXT): VALIDATION_ERROR?

  /**
   * Returns the correct WWW-Authenticate header value from the specified error.
   * @param acceptance the acceptance object returned by the delegate handler when accepting the request.
   * @param headers the request headers.
   * @param error the authentication error.
   * @return the WWW-Authenticate header value.
   */
  protected abstract fun wwwAuthenticate(acceptance: ACCEPTANCE,
                                         headers: Headers,
                                         error: VALIDATION_ERROR): String

  interface ValidationError

  /**
   * Base Acceptance object type for Authentication handlers.
   */
  class Acceptance<ACCEPTANCE: HttpHandler.Acceptance<*>>(
    internal val delegate: ACCEPTANCE
  ): HttpHandler.Acceptance<NoParams>(delegate.bodyAllowed, delegate.bodyRequired,
                                      delegate.method, delegate.uri, NoParams)

  /**
   * Base Context object type for Authentication handlers.
   */
  open class Context<out CONTEXT>(others: Collection<*>?,
                                  val delegate: CONTEXT): AbstractHttpHandler.Context(others)

  /**
   * Default response for unauthorized requests (401 Unauthorized with no body).
   */
  class UnauthorizedResponse: Response<Nothing>(Status.UNAUTHORIZED) {
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
