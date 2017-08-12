package info.jdavid.server

import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit


class Headers(private val lines: MutableList<String> = ArrayList(16)) {

  internal fun add(line: String) = lines.add(line)

  internal suspend fun aWrite(channel: AsynchronousSocketChannel, deadline: Long) {
    for (line in lines) {
      channel.aWrite(ByteBuffer.wrap(line.toByteArray(ISO_8859_1)),
                     deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
      channel.aWrite(ByteBuffer.wrap(CRLF),
                     deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
    }
    channel.aWrite(ByteBuffer.wrap(CRLF),
                   deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
  }

  internal suspend fun aWrite(channel: AsynchronousSocketChannel, deadline: Long, segment: ByteBuffer) {
    for (line in lines) {
      segment.put(line.toByteArray(ISO_8859_1))
      segment.put(CRLF)
    }
    segment.put(CRLF)
    segment.rewind()
    channel.aWrite(segment, deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
  }

  fun add(name: String, value: String) = lines.add("${name}: ${value}")

  fun value(name: String): String? {
    val lower = name.toLowerCase()
    return lines.findLast { matches(it, lower) }?.substring(name.length + 1)?.trim()
  }

  fun values(name: String): List<String> {
    val lower = name.toLowerCase()
    return lines.filter { matches(it, lower) }.map { it.substring(name.length + 1).trim() }
  }

  fun keys(): List<String> {
    return lines.associateTo(LinkedHashMap()) {
      val key = it.substring(0, it.indexOf(':'))
      key.toLowerCase() to key
    }.values.toList()
  }

  fun has(name: String): Boolean {
    val lower = name.toLowerCase()
    return lines.find { matches(it, lower) } != null
  }

  companion object {
    private val ISO_8859_1 = Charsets.ISO_8859_1
    private val CRLF: ByteArray = byteArrayOf(0x0d, 0x0a)
    private fun matches(line: String, lowercaseName: String): Boolean {
      return line.length > lowercaseName.length + 1 &&
        line.substring(0, lowercaseName.length).toLowerCase() == lowercaseName &&
        line[lowercaseName.length] == ':'
    }
  }

}
