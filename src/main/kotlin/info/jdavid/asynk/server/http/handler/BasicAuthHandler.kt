package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import java.util.Base64

/**
 * AuthHandler for Basic Authentication. This is a basic plaintext user/password authentication.<br>
 * Server:<br>
 * 401 Unauthorized<br>
 * WWW-Authenticate: Basic realm="Example"<br>
 * This indicates that basic authentication is required for accessing the resource, and that requests should
 * include the appropriate Authorization header. This credential header can be reused for other uris in the
 * same realm.
 *
 * @param realm the realm name.
 * @param delegate the delegate handler that should handle accepted requests with valid authentication.
 * @param ACCEPTANCE the delegate acceptance object type.
 * @param ACCEPTANCE_PARAMS the delegate acceptance object params type.
 * @param DELEGATE_CONTEXT the delegate context object type.
 * @param AUTH_CONTEXT the authentication context object type that wraps the delegate context. It can be used
 * to carry extra information used to validate credentials (a list of revoked tokens for instance).
 * @param ROUTE_PARAMS the parameter type captured by the route when matching the request.
 */
abstract class BasicAuthHandler<ACCEPTANCE: HttpHandler.Acceptance<ACCEPTANCE_PARAMS>,
                                ACCEPTANCE_PARAMS: Any,
                                DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                                AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                                ROUTE_PARAMS: Any>(
  delegate: HttpHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, DELEGATE_CONTEXT, ROUTE_PARAMS>,
  private val realm: String
): AuthHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, DELEGATE_CONTEXT, AUTH_CONTEXT, ROUTE_PARAMS,
               BasicAuthHandler.InvalidCredentialsError>(delegate) {

  final override suspend fun validateCredentials(acceptance: ACCEPTANCE,
                                                 headers: Headers,
                                                 context: AUTH_CONTEXT): InvalidCredentialsError? {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return InvalidCredentialsError
    return if (auth.startsWith("Basic ") &&
               credentialsAreValid(auth, context)) null else InvalidCredentialsError
  }

  /**
   * Returns whether the Authorization header value matches authorized credentials or not.
   * The helper method [userPassword] can be used to extract user and password from the header value.
   * @param auth the Authorization header value.
   * @param context the thread-level context object shared by all instances of this handler running on the
   * same thread.
   */
  abstract fun credentialsAreValid(auth: String,
                                   context: AUTH_CONTEXT): Boolean

  final override fun wwwAuthenticate(acceptance: ACCEPTANCE,
                                     headers: Headers,
                                     error: InvalidCredentialsError) =
    "Basic realm=\"$realm\", charset=\"UTF-8\""

  /**
   * Generic error reprensenting invalid or unauthorized credentials.
   */
  object InvalidCredentialsError: ValidationError

  companion object {
    /**
     * Builds the Authorization header value from a username and password.
     * @param username the username.
     * @param password the password.
     * @return the Authorization header value.
     */
    fun authorizationHeaderValue(username: String, password: String): String {
      val b64 = Base64.getEncoder().encodeToString("${username}:${password}".toByteArray())
      return "Basic ${b64}"
    }

    /**
     * Extracts the username and password from the Authorization header value.
     * @param authorizationHeaderValue the Authorization header value.
     * @return the username/password as a key/value pair.
     */
    fun userPassword(authorizationHeaderValue: String): Pair<String, String> {
      val decoded = String(Base64.getDecoder().decode(authorizationHeaderValue.substring(6)))
      val index = decoded.indexOf(':')
      if (index == -1) throw IllegalArgumentException("Invalid basic authorization header.")
      return decoded.substring(0, index) to decoded.substring(index + 1)
    }

    /**
     * Adds basic authentication to an existing [HttpHandler].
     * @param realm the authentication realm.
     * @param delegate the delegate handler.
     * @param userPassword a function that given a username, returns its password, or returns null if there
     * is no authorized user with that username.
     * @return the auth handler.
     */
    @Suppress("UNCHECKED_CAST")
    fun of(realm: String,
           delegate: HttpHandler<*,*,*,*>,
           userPassword: (user: String) -> String?) =
      object: BasicAuthHandler<HttpHandler.Acceptance<Any>, Any,
                               AbstractHttpHandler.Context,
                               AuthHandler.Context<AbstractHttpHandler.Context>,
                               Any>(delegate as HttpHandler<HttpHandler.Acceptance<Any>, Any,
                                                            AbstractHttpHandler.Context, Any>, realm) {
        override fun credentialsAreValid(auth: String,
                                         context: AuthHandler.Context<AbstractHttpHandler.Context>): Boolean {
          val (user, password) = BasicAuthHandler.userPassword(auth)
          return (userPassword(user) ?: return false) == password
        }
        override suspend fun context(others: Collection<*>?) =
          AuthHandler.Context(others, delegate.context(others))
      }
  }

}
