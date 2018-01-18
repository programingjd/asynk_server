package info.jdavid.server

import com.codahale.aesgcmsiv.AEAD
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.Key
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac

object Crypto {

  private val HMAC_SHA256 = "HmacSHA256"

  private val AES = "AES"
  private val ZERO = "0"

  init {
    if (!algorithmIsAvailable(AES)) throw RuntimeException("No suitable algorithm found.")
    if (!macIsAvailable(HMAC_SHA256)) throw RuntimeException("No suitable mac found.")
  }

  private fun macIsAvailable(algorithm: String): Boolean {
    return try {
      Mac.getInstance(algorithm)
      true
    }
    catch (ignore: GeneralSecurityException) {
      false
    }
  }

  private fun algorithmIsAvailable(algorithm: String): Boolean {
    return try {
      KeyGenerator.getInstance(algorithm)
      true
    }
    catch (ignore: GeneralSecurityException) {
      false
    }
  }

  fun sign(key: Key, bytes: ByteArray) = Mac.getInstance(HMAC_SHA256).let {
    it.init(key)
    hex(it.doFinal())
  }

  fun encrypt(key: Key, iv: ByteArray, bytes: ByteArray) = AEAD(key.encoded).seal(bytes, iv)

  fun decrypt(key: Key, iv: ByteArray, crypted: ByteArray) = AEAD(key.encoded).open(crypted, iv).get()

  fun iv(seed: ByteArray) = ByteArray(32).apply {
    SecureRandom(seed).nextBytes(this)
  }

  // Works for AEAD but also for HMAC
  fun secretKey(iv: ByteArray) = KeyGenerator.getInstance(AES).let {
    it.init(SecureRandom(iv))
    it.generateKey()
  }

  fun hex(n: BigInteger): String {
    val s = n.toString(16)
    return if (s.length % 2 == 0) s else ZERO + s
  }

  fun hex(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    var i = 0
    for (b in bytes) {
      chars[i++] = Character.forDigit(b.toInt().shr(4).and(0xf), 16)
      chars[i++] = Character.forDigit(b.toInt().and(0xf), 16)
    }
    return String(chars)
  }

  fun hex(buffer: ByteBuffer): String {
    val chars = CharArray(buffer.remaining() * 2)
    var i = 0
    while (buffer.remaining() > 0) {
      val b = buffer.get()
      chars[i++] = Character.forDigit(b.toInt().shr(4).and(0xf), 16)
      chars[i++] = Character.forDigit(b.toInt().and(0xf), 16)
    }
    return String(chars)
  }

  fun unhex(hex: String): ByteArray {
    return ByteArray(hex.length / 2, {
      (hexDigit(hex[it*2]).shl(4) + hexDigit(hex[it*2+1])).toByte()
    })
  }

  fun unhex(buffer: ByteBuffer, hex: String) {
    for (i in 0 until hex.length / 2) {
      buffer.put((hexDigit(hex[i*2]).shl(4) + hexDigit(hex[i*2+1])).toByte())
    }
  }

  private fun hexDigit(c: Char): Int {
    if (c <= '9') return c - '0'
    if (c <= 'F') return c - 'A' + 10
    if (c <= 'f') return c - 'a' + 10
    throw IllegalArgumentException("Unexpected hex digit: ${c}")
  }

}
