package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Crypto
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.collections.HashMap

abstract class DigestAuthHandler<ACCEPTANCE: HttpHandler.Acceptance<PARAMS>,
                                 DELEGATE_CONTEXT: AbstractHttpHandler.Context,
                                 AUTH_CONTEXT: AuthHandler.Context<DELEGATE_CONTEXT>,
                                 PARAMS: Any>(
  delegate: HttpHandler<ACCEPTANCE, DELEGATE_CONTEXT, PARAMS>,
  domainUris: List<String> = listOf("/")
): AuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS,
               DigestAuthHandler.InvalidAuthorizationErrors>(delegate) {

  private val domain = domainUris.joinToString(" ") {it.replace("\"", "\\\"") }

  final override suspend fun validateCredentials(acceptance: ACCEPTANCE, headers: Headers,
                                                 context: AUTH_CONTEXT): InvalidAuthorizationErrors? {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return InvalidAuthorizationErrors.GENERIC
    if (!auth.startsWith("Digest ")) return InvalidAuthorizationErrors.GENERIC
    val host = headers.value(Headers.HOST) ?: return InvalidAuthorizationErrors.GENERIC

    val map = map(auth)
    val uri = map[URI] ?: return InvalidAuthorizationErrors.GENERIC
    if (uri != acceptance.uri) return InvalidAuthorizationErrors.GENERIC

    val realm = realm(host, acceptance.uri)
    if (map[REALM] != realm) return InvalidAuthorizationErrors.GENERIC

    val username = map[USERNAME] ?: return InvalidAuthorizationErrors.GENERIC

    val algorithm = when (map[ALGORITHM]) {
      null, "MD5", "MD5-sess" -> Algorithm.MD5
      "SHA-256", "SHA-256-sess" -> Algorithm.SHA256
      else -> return InvalidAuthorizationErrors.GENERIC
    }
    if (algorithm != algorithm()) return InvalidAuthorizationErrors.GENERIC

    if (map[QOP] != "auth") return InvalidAuthorizationErrors.GENERIC

    val opaque = map[OPAQUE] ?: return InvalidAuthorizationErrors.GENERIC
    val opaqueError = validateOpaque(opaque, realm)
    if (opaqueError != null) return opaqueError

    val nonce = map[NONCE] ?: return InvalidAuthorizationErrors.GENERIC
    val nounceError = validateNonce(nonce, host, realm)
    if (nounceError != null) return nounceError

    val cnonce = map[CNONCE] ?: return InvalidAuthorizationErrors.GENERIC

    val nc = map[NC] ?: return InvalidAuthorizationErrors.GENERIC

    val ha1 =
      ha1(username, context, algorithm, realm, nonce, cnonce) ?: return InvalidAuthorizationErrors.GENERIC

    val response = map[RESPONSE] ?: return InvalidAuthorizationErrors.GENERIC

    val ha2 = h("${acceptance.method}:${uri}", algorithm)
    val expected = h("${ha1}:${nonce}:${nc}:${cnonce}:auth:${ha2}", algorithm)
    return if (expected == response) null else InvalidAuthorizationErrors.GENERIC
  }

  /**
   * @return a byte array 32 bytes long
   */
  protected open fun salt(): ByteArray = SecureRandom().generateSeed(32)

  protected val seed by lazy {
    salt().apply { if (size != 32) throw RuntimeException("Salt should be 32 bytes long.") }
  }

  protected open fun realm(host: String, uri: String) = host

  protected open fun opaque(realm: String) = h("${seed}:${realm}", algorithm())

  protected open fun validateOpaque(opaque: String, realm: String): InvalidAuthorizationErrors? {
    return if (opaque(realm) == opaque) null else InvalidAuthorizationErrors.GENERIC
  }

//  protected open fun nonce(host: String, realm: String) = h("${host}:${seed}:${realm}", algorithm())
//
//  protected open fun validateNonce(nonce: String, host: String, realm: String): InvalidAuthorizationErrors? {
//    return if (nonce(host, realm) == nonce) null else InvalidAuthorizationErrors.GENERIC
//  }

  private val key = Crypto.secretKey(seed)
  private val nonceIv = Crypto.iv(seed)

  protected open fun nonce(host: String, realm: String): String {
    val time = Crypto.hex(BigInteger.valueOf(System.currentTimeMillis()))
    val rand = Crypto.hex(SecureRandom().generateSeed(32))
    return Crypto.hex(Crypto.encrypt(
      key, nonceIv, "${time}${rand}${host}".toByteArray(Charsets.US_ASCII)
    ))
  }

  protected open fun validateNonce(nonce: String, host: String, realm: String): InvalidAuthorizationErrors? {
    val decrypted =
      Crypto.decrypt(key, nonceIv, Crypto.unhex(nonce))?.let {
        String(it, Charsets.US_ASCII)
      } ?: return InvalidAuthorizationErrors.GENERIC
    val time = decrypted.substring(0, 12).toLong(16)
    if ((System.currentTimeMillis() - time) > 600000) return InvalidAuthorizationErrors.STALE_NONCE // >10mins
    if (decrypted.substring(76) != host) return InvalidAuthorizationErrors.GENERIC
    return null
  }

  internal open fun ha1(username: String, context: AUTH_CONTEXT,
                        algorithm: Algorithm, realm: String,
                        nonce: String, cnonce: String) = ha1(username, context, algorithm, realm)

  protected abstract fun ha1(username: String, context: AUTH_CONTEXT,
                             algorithm: Algorithm, realm: String): String?

  final override fun wwwAuthenticate(acceptance: ACCEPTANCE, headers: Headers,
                                     error: InvalidAuthorizationErrors): String {
    val host = headers.value(Headers.HOST) ?: throw RuntimeException()
    val realm = realm(host, acceptance.uri)
    val nonce = nonce(host, realm)
    val opaque = opaque(realm)
    return if (error == InvalidAuthorizationErrors.STALE_NONCE) {
      "Digest realm=\"${realm}\", domain=\"${domain}\", qop=\"auth\", algorithm=${algorithmKey()}, stale=true, nonce=\"${nonce}\", opaque=\"${opaque}\""
    }
    else {
      "Digest realm=\"${realm}\", domain=\"${domain}\", qop=\"auth\", algorithm=${algorithmKey()}, nonce=\"${nonce}\", opaque=\"${opaque}\""
    }
  }

  protected open fun algorithm() = Algorithm.MD5

  internal open fun algorithmKey() = algorithm().key

  protected fun ha1(username: String, password: String, algorithm: Algorithm, realm: String): String? {
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
    domainUris: List<String> = listOf("/")
  ): DigestAuthHandler<ACCEPTANCE, DELEGATE_CONTEXT, AUTH_CONTEXT, PARAMS>(delegate, domainUris) {
    final override fun ha1(username: String, context: AUTH_CONTEXT, algorithm: Algorithm, realm: String) =
      throw UnsupportedOperationException("nonce and cnonce are required in the session variant.")
    final override fun throwIfSession() =
      throw UnsupportedOperationException("nonce and cnonce are required in the session variant.")

    abstract override fun ha1(username: String, context: AUTH_CONTEXT,
                              algorithm: Algorithm, realm: String, nonce: String, cnonce: String): String?

    protected fun ha1(username: String, password: String,
                      algorithm: Algorithm, realm: String, nonce: String, cnonce: String) =
      h(h("${username}:${realm}:${password}", algorithm) + ":" + nonce + ":" + cnonce, algorithm)

    override fun algorithm() = Algorithm.SHA256

    override fun algorithmKey() = "${algorithm().key}-sess"

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
