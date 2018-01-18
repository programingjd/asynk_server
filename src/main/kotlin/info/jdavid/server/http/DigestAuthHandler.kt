package info.jdavid.server.http

import info.jdavid.server.Crypto
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import kotlin.collections.HashMap

abstract class DigestAuthHandler<A: HttpHandler.Acceptance,
                                 C: AbstractHttpHandler.Context,
                                 D: AuthHandler.Context<C>>(
  delegate: HttpHandler<A, C>,
  private val realm: String,
  seed: ByteArray
): AuthHandler<A, C, D>(delegate) {

  private val key = Crypto.secretKey(SecureRandom(seed).generateSeed(32))
  private val nonceIv = Crypto.iv(seed)

  final override suspend fun credentialsAreValid(acceptance: A, headers: Headers, context: D): Boolean {
    val auth = headers.value(Headers.AUTHORIZATION) ?: return false
    val matcher = PATTERN.matcher(auth.substring(auth.indexOf(' ') + 1))
    val map = HashMap<String, String>(12)
    while (matcher.find()) {
      val key = matcher.group(1)
      for (i in 2..5) {
        if (matcher.group(i) != null) {
          map.put(key, matcher.group(i))
          break
        }
      }
    }
    val host = headers.value(Headers.HOST) ?: return false
    if (map[REALM] != realm) return false
    val username = map[USERNAME] ?: return false
    val ha1 = ha1(username, context) ?: return false

    val uri = map[URI] ?: return false
    if (uri != acceptance.uri) return false
    val nonce = map[NONCE] ?: return false
    val decrypted = String(Crypto.decrypt(key, nonceIv, Crypto.unhex(nonce)), Charsets.US_ASCII)
    val time = decrypted.substring(0, 12).toLong(16)
    if ((System.currentTimeMillis() - time) > 600000) return false // nonce older than 10 mins
    if (decrypted.substring(76) != "${host}${uri}") return false
    if (map[QOP] != "auth") return false
    val nc = map[NC] ?: return false
    val cnonce = map[CNONCE] ?: return false
    if (map[OPAQUE] != opaque(host)) return false
    val response = map[RESPONSE] ?: return false

    val ha2 = Crypto.hex(md5("${acceptance.method}:${uri}"))
    val expected = Crypto.hex(md5("${ha1}:${nonce}:${nc}:${cnonce}:auth:${ha2}"))
    return expected == response
  }

  abstract fun ha1(username: String, context: D): String?

  final override fun wwwAuthenticate(acceptance: A, headers: Headers): String {
    val host = headers.value(Headers.HOST) ?: throw RuntimeException()
    val time = Crypto.hex(BigInteger.valueOf(System.currentTimeMillis()))
    val rand = Crypto.hex(SecureRandom().generateSeed(32))
    val nonce = Crypto.hex(Crypto.encrypt(
      key, nonceIv, "${time}${rand}${host}${acceptance.uri}".toByteArray(Charsets.US_ASCII)
    ))
    val opaque = opaque(host)
    return "Digest realm=\"${realm}\", qop=\"auth\", algorithm=MD5, nonce=\"${nonce}\", opaque=\"${opaque}\""
  }

  private fun opaque(host: String) = Base64.getEncoder().encodeToString("${realm}@${host}".toByteArray())

  protected fun ha1(username: String, password: String) = ha1(username, password, realm)

  companion object {
    private val PATTERN = "([^=]+)=(?:\"([^\"]*)\"|([0-9a-f]{8})|(auth)|(MD5)),?\\s?".toPattern()
    private val USERNAME = "username"
    private val REALM = "realm"
    private val NONCE = "nonce"
    private val URI = "uri"
    private val QOP = "qop"
    private val NC = "nc"
    private val CNONCE = "cnonce"
    private val RESPONSE = "response"
    private val OPAQUE = "opaque"
    private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray())
    fun ha1(username: String, password: String,
            realm: String) = Crypto.hex(md5("${username}:${realm}:${password}"))
  }

}
