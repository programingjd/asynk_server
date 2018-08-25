package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Crypto
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import kotlin.collections.HashMap

abstract class DigestAuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                                 DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                                 AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                                 PARAMS: Any>(
  delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>,
  private val realm: String,
  domainUris: List<String> = listOf("/"),
  seed: ByteArray = SecureRandom().generateSeed(32)
): AuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS,
               DigestAuthHandler.InvalidAuthorizationErrors>(delegate) {

  private val domain = domainUris.joinToString(" ") {it.replace("\"", "\\\"") }
  private val key = Crypto.secretKey(SecureRandom(seed).generateSeed(32))
  private val nonceIv = Crypto.iv(seed)

  final override suspend fun validateCredentials(acceptance: ACCEPTANCE, headers: Headers,
                                                 context: AUTH_CONTEXT): InvalidAuthorizationErrors? {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return InvalidAuthorizationErrors.GENERIC
    if (!auth.startsWith("Digest ")) return InvalidAuthorizationErrors.GENERIC
    val host = headers.value(Headers.HOST) ?: return InvalidAuthorizationErrors.GENERIC

    val map = map(auth)
    if (map[REALM] != realm) return InvalidAuthorizationErrors.GENERIC

    val uri = map[URI] ?: return InvalidAuthorizationErrors.GENERIC
    if (uri != acceptance.uri) return InvalidAuthorizationErrors.GENERIC

    val username = map[USERNAME] ?: return InvalidAuthorizationErrors.GENERIC

    val algorithm = when (map[ALGORITHM]) {
      null, "MD5" -> Algorithm.MD5
      "SHA-256" -> Algorithm.SHA256
      else -> return InvalidAuthorizationErrors.GENERIC
    }
    if (algorithm != algorithm()) return InvalidAuthorizationErrors.GENERIC

    if (map[QOP] != "auth") return InvalidAuthorizationErrors.GENERIC

    if (map[OPAQUE] != opaque(host)) return InvalidAuthorizationErrors.GENERIC

    val nonce = map[NONCE] ?: return InvalidAuthorizationErrors.GENERIC
    val decryptedNounce =
      Crypto.decrypt(key, nonceIv, Crypto.unhex(nonce))?.let {
        String(it, Charsets.US_ASCII)
      } ?: return InvalidAuthorizationErrors.GENERIC
    val time = decryptedNounce.substring(0, 12).toLong(16)
    if ((System.currentTimeMillis() - time) > 600000) return InvalidAuthorizationErrors.STALE_NONCE // >10mins
    if (decryptedNounce.substring(76) != "${host}${uri}") return InvalidAuthorizationErrors.GENERIC

    val cnonce = map[CNONCE] ?: return InvalidAuthorizationErrors.GENERIC

    val nc = map[NC] ?: return InvalidAuthorizationErrors.GENERIC

    val ha1 = ha1(username, context, algorithm, nonce, cnonce) ?: return InvalidAuthorizationErrors.GENERIC

    val response = map[RESPONSE] ?: return InvalidAuthorizationErrors.GENERIC

    val ha2 = h("${acceptance.method}:${uri}", algorithm)
    val expected = h("${ha1}:${nonce}:${nc}:${cnonce}:auth:${ha2}", algorithm)
    return if (expected == response) null else InvalidAuthorizationErrors.GENERIC
  }

  internal open fun ha1(username: String, context: AUTH_CONTEXT, algorithm: Algorithm,
                        nonce: String, cnonce: String) = ha1(username, context, algorithm)

  protected abstract fun ha1(username: String, context: AUTH_CONTEXT, algorithm: Algorithm): String?

  final override fun wwwAuthenticate(acceptance: ACCEPTANCE, headers: Headers,
                                     error: InvalidAuthorizationErrors): String {
    val host = headers.value(Headers.HOST) ?: throw RuntimeException()
    val time = Crypto.hex(BigInteger.valueOf(System.currentTimeMillis()))
    val rand = Crypto.hex(SecureRandom().generateSeed(32))
    val nonce = Crypto.hex(Crypto.encrypt(
      key, nonceIv, "${time}${rand}${host}${acceptance.uri}".toByteArray(Charsets.US_ASCII)
    ))
    val opaque = opaque(host)
    return if (error == InvalidAuthorizationErrors.STALE_NONCE) {
      "Digest realm=\"${realm}\", domain=\"${domain}\", qop=\"auth\", algorithm=${algorithm().key}, stale=true, nonce=\"${nonce}\", opaque=\"${opaque}\""
    }
    else {
      "Digest realm=\"${realm}\", domain=\"${domain}\", qop=\"auth\", algorithm=${algorithm().key}, nonce=\"${nonce}\", opaque=\"${opaque}\""
    }
  }

  protected open fun algorithm() = Algorithm.MD5

  protected open fun opaque(host: String) =
    Base64.getEncoder().encodeToString("${realm}@${host}".toByteArray())

  protected fun ha1(username: String, password: String, algorithm: Algorithm): String? {
    throwIfSession()
    return h("${username}:${realm}:${password}", algorithm)
  }

  internal open fun throwIfSession() = null

  enum class Algorithm(internal val key: String, internal val digest: MessageDigest?) {
    MD5("MD5", try { MessageDigest.getInstance("MD5") } catch (ignore: Exception) { null }),
    SHA256("SHA-256", try { MessageDigest.getInstance("SHA-256") } catch (ignore: Exception) { null })
  }

  abstract class Session<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
    DELEGATE_CONTEXT: AbstractHttpHandler.Context,
    AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
    PARAMS: Any>(
    delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>,
    val realm: String,
    domainUris: List<String> = listOf("/"),
    seed: ByteArray = SecureRandom().generateSeed(32)
  ): DigestAuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS>(delegate, realm, domainUris, seed) {
    final override fun ha1(username: String, context: AUTH_CONTEXT, algorithm: Algorithm) =
      throw UnsupportedOperationException("nonce and cnonce are required in the session variant.")
    final override fun throwIfSession() =
      throw UnsupportedOperationException("nonce and cnonce are required in the session variant.")

    abstract override fun ha1(username: String, context: AUTH_CONTEXT,
                              algorithm: Algorithm, nonce: String, cnonce: String): String?

    protected fun ha1(username: String, password: String,
                      algorithm: Algorithm, nonce: String, cnonce: String) =
      h(h("${username}:${realm}:${password}", algorithm) + ":" + nonce + ":" + cnonce, algorithm)
  }

  enum class InvalidAuthorizationErrors: ValidationError {
    GENERIC,
    STALE_NONCE
  }

  companion object {
    private val PATTERN = "([^=]+)=(?:\"([^\"]*)\"|([0-9a-f]{8})|(auth)|(MD5)),?\\s?".toPattern()
    private const val USERNAME = "username"
    private const val REALM = "realm"
    private const val ALGORITHM = "algorithm"
    private const val NONCE = "nonce"
    private const val URI = "uri"
    private const val QOP = "qop"
    private const val NC = "nc"
    private const val CNONCE = "cnonce"
    private const val RESPONSE = "response"
    private const val OPAQUE = "opaque"
    private fun h(text: String, algorithm: Algorithm) = Crypto.hex(
      (algorithm.digest ?: throw RuntimeException("Unsupported digest algorithm.")).digest(text.toByteArray())
    )
    private fun map(auth: String): Map<String, String> {
      val map = HashMap<String, String>(12)
      val matcher = PATTERN.matcher(auth.substring(7))
      while (matcher.find()) {
        val key = matcher.group(1)
        for (i in 2..5) {
          if (matcher.group(i) != null) {
            map[key] = matcher.group(i)
            break
          }
        }
      }
      return map
    }
    fun user(authorizationHeaderValue: String) = map(authorizationHeaderValue)[USERNAME]
  }

}
