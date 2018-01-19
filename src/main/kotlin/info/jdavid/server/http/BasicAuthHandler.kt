package info.jdavid.server.http

import java.util.Base64

abstract class BasicAuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                                DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                                AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                                out PARAMS: Any>(
  delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>,
  private val realm: String
): AuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS>(delegate) {

  final override suspend fun credentialsAreValid(acceptance: ACCEPTANCE,
                                                 headers: Headers,
                                                 context: AUTH_CONTEXT): Boolean {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return false
    return credentialsAreValid(auth, context)
  }

  abstract fun credentialsAreValid(auth: String,
                                   context: AUTH_CONTEXT): Boolean

  override fun wwwAuthenticate(acceptance: ACCEPTANCE,
                               headers: Headers) = "Basic realm=\"$realm\", charset=\"UTF-8\""

  companion object {
    fun authorizationHeaderValue(user: String, password: String): String {
      val b64 = Base64.getEncoder().encodeToString("${user}:${password}".toByteArray())
      return "Basic ${b64}"
    }
  }

}
