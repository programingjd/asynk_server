@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

interface Handler<C> {

  fun context(): C

  suspend fun connect(remoteAddress: InetSocketAddress): Boolean

  suspend fun handle(socket: AsynchronousSocketChannel,
                     buffer: ByteBuffer,
                     context: C)

  companion object {
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

  open class Acceptance(val bodyAllowed: Boolean, val bodyRequired: Boolean)

}
