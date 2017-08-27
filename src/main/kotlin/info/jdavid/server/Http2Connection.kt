package info.jdavid.server

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("UsePropertyAccessSyntax")
internal class Http2Connection(val channel: Channel,
                               val readTimeoutMillis: Long, val writeTimeoutMillis: Long): Closeable {
  private var nextStreamId = 2
  private var nextPingId = 2
  private var lastGoogStreamId = 0
  private var unacknowledgedBytesRead = 0
  private var bytesLeftInWriteWindow = 65535
  private var receivedInitialPeerSetttings = false
  private val peerSettings = Settings().
    set(Settings.HEADER_TABLE_SIZE, bytesLeftInWriteWindow).
    set(Settings.MAX_FRAME_SIZE, 16384)

  suspend fun start(): Http2Connection {
    val writeDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
    //writePreface(writeDeadline)
    //writeSettings(Settings(), writeDeadline)
    val readDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)
    readPreface(readDeadline)
    readFrame(readDeadline)

    TODO()
  }

  override fun close() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  suspend private fun writePreface(deadline: Long) {
    channel.write(CONNECTION_PREFACE, deadline)
  }

  suspend private fun writeSettings(settings: Settings, deadline: Long) {
    val segment = frameHeader(0, settings.size() * 6, Types.SETTINGS, 0x00)
    for (i in 0 until settings.size()) {
      if (settings.isSet(i)) {
        segment.putShort(i.toShort())
        segment.putInt(settings.get(i))
      }
    }
    channel.write(segment, deadline)
  }

  suspend private fun readPreface(deadline: Long) {
    channel.read(CONNECTION_PREFACE.size, deadline)
  }

  suspend private fun readFrame(deadline: Long) {
    val segment = channel.read(9, deadline)
    segment.rewind().limit(segment.capacity())
    val length = (segment.get().toInt() and 0xff shl 16) or
                 (segment.get().toInt() and 0xff shl 8) or
                 (segment.get().toInt() and 0xff)
    val type = segment.get().toInt().and(0xff).toByte()
    val flags = segment.get().toInt().and(0xff)
    val streamId = segment.getInt() and 0x7fffffff
    println(streamId)
    when (streamId) {
      Types.DATA -> TODO()
      Types.HEADERS -> TODO()
      Types.PRIORITY -> TODO()
      Types.RST_STREAM -> TODO()
      Types.SETTINGS -> {
        if (flags and 0x01 == 0) readSettings(length, deadline)
      }
      Types.PUSH_PROMISE -> TODO()
      Types.PING -> TODO()
      Types.GOAWAY -> TODO()
      Types.WINDOW_UPDATE -> TODO()
      else -> TODO()
    }
  }

  suspend private fun readSettings(length: Int, deadline: Long) {
    val segment = channel.read(length * 6, deadline)
    segment.rewind().limit(segment.capacity())
    val settings = Settings()
    for (i in 0 until length) {
      val id = segment.getShort().toInt()
      val value = segment.getInt()
      settings[id] = value
    }
    // set settings
  }

  private fun frameHeader(streamId: Int, length: Int, type: Int, flags: Int): ByteBuffer {
    val segment = channel.segment()
    segment.rewind().limit(segment.capacity())
    segment.put((length.ushr(16) and 0xff).toByte())
    segment.put((length.ushr(8) and 0xff).toByte())
    segment.put((length and 0xff).toByte())
    segment.put((type and 0xff).toByte())
    segment.put((flags and 0xff).toByte())
    segment.putInt(streamId and 0x7fffffff)
    return segment
  }

  companion object {
    val ASCII = Charsets.US_ASCII
    private val CONNECTION_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(ASCII)


  }

  internal class Types {

    companion object {
      internal val DATA = 0x00
      internal val HEADERS = 0x01
      internal val PRIORITY = 0x02
      internal val RST_STREAM = 0x03
      internal val SETTINGS = 0x04
      internal val PUSH_PROMISE = 0x05
      internal val PING = 0x06
      internal val GOAWAY = 0x07
      internal val WINDOW_UPDATE = 0x08
      internal val CONTINUATION = 0x09
    }

    internal class Flags {
      companion object {
        internal val END_STREAM = 0x01
        internal val END_HEADERS = 0x04
        internal val PADDED = 0x08
        internal val PRIORITY = 0x20
      }
    }

  }

  internal class Flags {

    companion object {
      internal val END_STREAM = 0x01
      internal val END_HEADERS = 0x04
      internal val PADDED = 0x08
      internal val PRIORITY = 0x20
    }

  }

  internal class Settings {

    private var bitset: Int = 0
    private val values = IntArray(6)

    internal fun clear() {
      bitset = 0
      Arrays.fill(values, 0)
    }

    operator fun set(id: Int, value: Int): Settings {
      if (id < 0 || id >= values.size) return this
      val bit = 1 shl id
      bitset = bitset or bit
      values[id] = value
      return this
    }

    fun isSet(id: Int): Boolean {
      val bit = 1 shl id
      return bitset and bit != 0
    }

    operator fun get(id: Int) = values[id]

    fun size() = Integer.bitCount(bitset)

    fun count() = values.size

    internal fun merge(other: Settings) {
      for (i in 0 until 6) {
        if (other.isSet(i)) set(i, other[i])
      }
    }

    companion object {
      internal val HEADER_TABLE_SIZE: Int = 0x01
      internal val ENABLE_PUSH: Int = 0x02
      internal val MAX_CONCURRENT_STREAMS: Int = 0x03
      internal val INITIAL_WINDOW_SIZE: Int = 0x04
      internal val MAX_FRAME_SIZE: Int = 0x05
      internal val MAX_HEADER_LIST_SIZE: Int = 0x06
    }

  }

}
