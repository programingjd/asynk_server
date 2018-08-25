package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import java.util.Base64

abstract class BasicAuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                                DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                                AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                                PARAMS: Any>(
  delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>,
  private val realm: String
): AuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS,
               BasicAuthHandler.InvalidCredentialsError>(delegate) {

  final override suspend fun validateCredentials(acceptance: ACCEPTANCE,
                                                 headers: Headers,
                                                 context: AUTH_CONTEXT): InvalidCredentialsError? {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return InvalidCredentialsError
    return if (auth.startsWith("Basic ") &&
               credentialsAreValid(auth, context)) null else InvalidCredentialsError
  }

  abstract fun credentialsAreValid(auth: String,
                                   context: AUTH_CONTEXT): Boolean

  override fun wwwAuthenticate(acceptance: ACCEPTANCE,
                               headers: Headers,
                               error: InvalidCredentialsError) = "Basic realm=\"$realm\", charset=\"UTF-8\""

  object InvalidCredentialsError: ValidationError

  companion object {
    fun authorizationHeaderValue(user: String, password: String): String {
      val b64 = Base64.getEncoder().encodeToString("${user}:${password}".toByteArray())
      return "Basic ${b64}"
    }
    fun userPassword(authorizationHeaderValue: String): Pair<String, String> {
      val decoded = String(Base64.getDecoder().decode(authorizationHeaderValue.substring(6)))
      val index = decoded.indexOf(':')
      if (index == -1) throw IllegalArgumentException("Invalid basic authorization header.")
      return decoded.substring(0, index) to decoded.substring(index + 1)
    }
  }

}
