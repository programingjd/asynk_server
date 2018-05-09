package info.jdavid.server

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class CryptoTests {

  // Depends on SecureRandom implementation (fails on windows jdk 8 for instance).
  /*@Test*/ fun testIv() {
    val iv1 = Crypto.iv("Hardcoded seed only for testing".toByteArray())
    val iv2 = Crypto.iv("Hardcoded seed only for testing".toByteArray())
    assertNotEquals(Crypto.hex(iv1), Crypto.hex(iv2))
  }

  @Test fun testCryptEncrypt() {
    val iv1 = Crypto.unhex("a45c9012c9d76759a533df52d6db392b")
    val key1 = Crypto.secretKey(iv1)

    val key = SecretKeySpec(Crypto.unhex("3f69b3f5a5855f116ec878cec91b340d"), key1.algorithm)
    val crypted = Crypto.encrypt(key, iv1, "Super secret message".toByteArray())
    assertEquals("Super secret message", String(Crypto.decrypt(key, iv1, crypted)))
  }

  @Test fun testSign() {
    val iv1 = Crypto.unhex("a45c9012c9d76759a533df52d6db392b")
    println(Crypto.sign(Crypto.secretKey(iv1), "Super secret message".toByteArray()))
  }

}
