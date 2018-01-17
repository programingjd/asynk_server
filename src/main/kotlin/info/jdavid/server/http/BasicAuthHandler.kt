package info.jdavid.server.http

import java.util.Base64

abstract class BasicAuthHandler<A: HttpHandler.Acceptance,
                                C: AbstractHttpHandler.Context,
                                D: AuthHandler.Context<C>>(
  delegate: HttpHandler<A, C>,
  private val realm: String
): AuthHandler<A, C, D>(delegate) {

  final override suspend fun credentialsAreValid(acceptance: A, headers: Headers, context: D): Boolean {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return false
    return credentialsAreValid(auth, context)
  }

  abstract fun credentialsAreValid(auth: String, context: D): Boolean

  override fun wwwAuthenticate(acceptance: A, headers: Headers) = "Basic realm=\"$realm\", charset=\"UTF-8\""

  companion object {
    fun authorizationHeaderValue(user: String, password: String): String {
      val b64 = Base64.getEncoder().encodeToString("${user}:${password}".toByteArray())
      return "Basic ${b64}"
    }
  }

}
