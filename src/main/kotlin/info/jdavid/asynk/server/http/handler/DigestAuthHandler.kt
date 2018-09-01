package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Crypto
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.collections.HashMap

/**
 * AuthHandler for Digest Authentication. This is a user/password authentication, but unlike with
 * Basic Authentication, the password is not sent in clear text.<br>
 * Server:<br>
 * 401 Unauthorized<br>
 * WWW-Authenticate: Digest realm="Example",<br>
 *                          qop="auth",
 *                          nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093"
 *                          opaque="5ccc069c403ebaf9f0171e9517f40e41"<br>
 * This indicates that digest authentication is required for accessing the resource, and that requests should
 * include the appropriate Authorization header. This credential header can be reused for other uris in the
 * same realm.<br>
 * Most browsers implement RFC2617. There's also a draft RFC7616 with extensions to allow for the use
 * of SHA-256 hashing instead of MD5 and that adds a few more features. Those extensions are backward
 * compatible with older implementations.
 *
 * @param domainUris the list of root uris that fall under the protected space. This is an RFC7616 extension
 * and will be ignored by most clients. It defaults to "/" to indicate that all uris on this domain require
 * authentication.
 * @param delegate the delegate handler that should handle accepted requests with valid authentication.
 * @param ACCEPTANCE the delegate acceptance object type.
 * @param PARAMS the delegate acceptance object params type.
 * @param DELEGATE_CONTEXT the delegate context object type.
 * @param AUTH_CONTEXT the authentication context object type that wraps the delegate context. It can be used
 * to carry extra information used to validate credentials (a list of revoked tokens for instance).
 */
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
   * Returns a salt used for initializing encryption primitives. If you need the authorization headers
   * sent by the clients to still be valid after a server restart, you should override this function
   * and make it always return the same array of 32 bytes.<br>
   * This method should not be called directly. The [seed] property should be called instead.
   * @return a byte array 32 bytes long
   */
  protected open fun salt(): ByteArray = SecureRandom().generateSeed(32)

  protected val seed by lazy {
    salt().apply { if (size != 32) throw RuntimeException("Salt should be 32 bytes long.") }
  }

  /**
   * Returns the realm name (defaults to the host name).
   * @param host the host name (taken from the Host header value).
   * @param uri the request uri.
   * @return the realm name.
   */
  protected open fun realm(host: String, uri: String) = host

  /**
   * Returns the opaque for the given realm name. The opaque should stay the same for all requests of uris
   * within the same realm. Its string representation should be in hex or base64.<br>
   * The default implementation returns a hash of the seed and realm name. If this method is overridden, then
   * the [validateOpaque] method should be changed accordingly.
   * @param realm the realm name.
   * @return the opaque value.
   */
  protected open fun opaque(realm: String) = h("${seed}:${realm}", algorithm())

  /**
   * Validates the opaque value. It should check that the opaque matches the value returned by the [opaque]
   * method.
   * @param opaque the opaque value to verify.
   * @param realm the realm name.
   * @return an error if the opaque is not valid, or null if it is valid.
   */
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

  /**
   * Returns the server nonce for the request. Its string representation should be in hex or base64.
   * The nonce is used in the digest hashing algorithm. It should change for every unauthorized request.
   * It's a good idea to also include a time component and invalidate nonce values that are too old.<br>
   * The default implementation includes a random component, a time component, and the host value; all in a
   * crypted form. If this method is overridden, then the [validateNonce] method should be changed
   * accordingly.
   * @param host the host name (taken from the Host header value).
   * @param realm the realm name.
   * @return the nonce value.
   */
  protected open fun nonce(host: String, realm: String): String {
    val time = Crypto.hex(BigInteger.valueOf(System.currentTimeMillis()))
    val rand = Crypto.hex(SecureRandom().generateSeed(32))
    return Crypto.hex(Crypto.encrypt(
      key, nonceIv, "${time}${rand}${host}".toByteArray(Charsets.US_ASCII)
    ))
  }

  /**
   * Validates the nonce value. It should check that the nonce matches the value returned by the [nonce]
   * method.
   * @param nonce the nonce value to verify.
   * @param host the host name (taken from the Host header value).
   * @param realm the realm name.
   * @return an error if the nonce is not valid, or null if it is valid.
   */
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

  /**
   * Returns the ha1 hash component of the Authorization header value. The [ha1] method that takes a username
   * and password can be used to help create the ha1 value.
   * @param username the username.
   * @param context the thread-level context object shared by all instances of this handler running on the
   * same thread.
   * @param algorithm the hashing algorithm.
   * @param realm the realm name.
   * @return the ha1 value.
   */
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

  /**
   * Returns which hashing algorithm to use. This is MD5 by default because SHA-256 use is only defined in
   * the draft RFC7616 and few clients implement it.
   */
  protected open fun algorithm() = Algorithm.MD5

  internal open fun algorithmKey() = algorithm().key

  /**
   * Helper method used to create the ha1 hash component from a username and password.
   * @param username the username.
   * @param password the password.
   * @param algorithm the hashing algorithm.
   * @param realm the realm name.
   * @return the ha1 value.
   */
  protected fun ha1(username: String, password: String, algorithm: Algorithm, realm: String): String? {
    throwIfSession()
    return h("${username}:${realm}:${password}", algorithm)
  }

  internal open fun throwIfSession() = null

  /**
   * Hashing algorithms.
   */
  enum class Algorithm(internal val key: String, internal val digest: MessageDigest?) {
    MD5("MD5", try { MessageDigest.getInstance("MD5") } catch (ignore: Exception) { null }),
    SHA256("SHA-256", try { MessageDigest.getInstance("SHA-256") } catch (ignore: Exception) { null })
  }

  /**
   * Session variant of the Digest Authentication, as defined in the draft RFC7616.
   *
   * @param domainUris the list of root uris that fall under the protected space.
   * @param delegate the delegate handler that should handle accepted requests with valid authentication.
   * @param ACCEPTANCE the delegate acceptance object type.
   * @param PARAMS the delegate acceptance object params type.
   * @param DELEGATE_CONTEXT the delegate context object type.
   * @param AUTH_CONTEXT the authentication context object type that wraps the delegate context. It can be used
   * to carry extra information used to validate credentials (a list of revoked tokens for instance).
   */
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

    /**
     * Hashing algorithm. Since the session variant of the Digest Authentication is only defined in the
     * draft RFC7616, then the client should also support the use of the more secure SHA-256 algorithm.
     * Therefore, this is the algorithm that we use for the session variant.
     */
    final override fun algorithm() = Algorithm.SHA256

    final override fun algorithmKey() = "${algorithm().key}-sess"

  }

  /**
   * Validation errors (generic or stale nonce).
   */
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

    /**
     * Adds digest authentication to an existing [HttpHandler].
     * @param realm the authentication realm.
     * @param delegate the delegate handler.
     * @param userPassword a function that given a username, returns its password, or returns null if there
     * is no authorized user with that username.
     * @return the auth handler.
     */
    @Suppress("UNCHECKED_CAST")
    fun of(realm: String,
           delegate: HttpHandler<*, *, *>,
           userPassword: (user: String) -> String?) =
      object: DigestAuthHandler<HttpHandler.Acceptance<Any>,
                                AbstractHttpHandler.Context,
                                AuthHandler.Context<AbstractHttpHandler.Context>,
                                Any>(delegate as HttpHandler<HttpHandler.Acceptance<Any>,
                                AbstractHttpHandler.Context,
                                Any>) {
        override fun ha1(username: String, context: Context<AbstractHttpHandler.Context>,
                         algorithm: Algorithm, realm: String): String? {
          val password = userPassword(username) ?: return null
          return ha1(username, password, algorithm, realm)
        }
        override suspend fun context(others: Collection<*>?) =
          AuthHandler.Context(others, delegate.context(others))
        override fun realm(host: String, uri: String) = realm
      }

    /**
     * Extracts the username from an Authorization header value.
     * @param authorizationHeaderValue the Authorization header value.
     * @return the username.
     */
    fun user(authorizationHeaderValue: String) = map(authorizationHeaderValue)[USERNAME]
  }

}
