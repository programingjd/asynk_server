package info.jdavid.server

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

object Crypto {

  private val AES = "AES"
  private val AES_CBC = "AES/CBC/PKCS5Padding"
  private val ZERO = "0"

  init {
    KeyGenerator.getInstance(AES)
    Cipher.getInstance(AES_CBC)
  }

  fun encrypt(key: Key, iv: ByteArray, bytes: ByteArray) = Cipher.getInstance(AES_CBC).let {
    it.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
    hex(it.doFinal(bytes))
  }

  fun decrypt(key: Key, iv: ByteArray, crypted: String) = Cipher.getInstance(AES_CBC).let {
    it.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
    it.doFinal(unhex(crypted))
  }

  fun iv(seed: ByteArray) = ByteArray(16).apply {
    SecureRandom(seed).nextBytes(this)
  }

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
