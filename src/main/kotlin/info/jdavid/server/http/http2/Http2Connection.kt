package info.jdavid.server.http.http2

import info.jdavid.server.Channel
import info.jdavid.server.Connection
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import java.io.IOException
import kotlin.coroutines.experimental.CoroutineContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Suppress("UsePropertyAccessSyntax")
internal class Http2Connection(val context: CoroutineContext,
                               val channel: Channel,
                               buffers: LockFreeLinkedListHead,
                               val maxRequestSize: Int,
                               val readTimeoutMillis: Long,
                               val writeTimeoutMillis: Long): Connection(buffers) {
  private val settings = Settings().
    set(Settings.HEADER_TABLE_SIZE, 4096).
    set(Settings.MAX_CONCURRENT_STREAMS, 64).
    set(Settings.INITIAL_WINDOW_SIZE, 65535).
    set(Settings.MAX_FRAME_SIZE, 16384)
  private val streams = LockFreeLinkedListHead()

  override fun next() {
    streams.forEach<Stream> { it.recycle() }
  }

  override fun recycle() {
    streams.forEach<Stream> {
      // todo, put back into buffers
    }
  }

  suspend fun start(): Http2Connection {
    val writeDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
    val readDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)

//    val stream = connectionPreface(readDeadline, writeDeadline)

    return this
  }

  suspend override fun close() {
    TODO("not implemented")
  }


//  suspend fun connectionPreface(readDeadline: Long, writeDeadline: Long): Stream {
//    val stream = Stream(0, buffers, maxRequestSize)
//    val client = async(context) {
//      val connectionPreface = channel.read(readDeadline, stream.segmentR, CONNECTION_PREFACE.size)
//      @Suppress("LoopToCallChain")
//      for (i in 0 until CONNECTION_PREFACE.size) {
//        if (connectionPreface[i] != CONNECTION_PREFACE[i]) {
//          throw IOException("Invalid connection preface.")
//        }
//      }
//      val settings =
//        Frame.read(channel, readDeadline) as? Frame.Settings ?: throw ConnectionException.ProtocolError()
//      if (settings.isAck) throw ConnectionException.ProtocolError()
//      this@Http2Connection.settings.merge(settings)
//    }
//    val server = async(context) {
//      //Frame.Settings(0, 0, Settings().write(channel))
//    }
//    return Stream(0)
//  }

  companion object {
    val ASCII = Charsets.US_ASCII
    private val CONNECTION_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(ASCII)


  }

  class Types {

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

  class Flags {

    companion object {
      internal val END_STREAM = 0x01
      internal val END_HEADERS = 0x04
      internal val PADDED = 0x08
      internal val PRIORITY = 0x20
    }

  }

  class Settings {

    private val bitset = AtomicInteger()
    private val values = Array<AtomicInteger>(6, { AtomicInteger() })

    fun clear() {
      bitset.set(0)
      values.forEach { it.set(0) }
    }

    operator fun set(id: Int, value: Int): Settings {
      if (id < 0 || id >= values.size) return this
      val bit = 1 shl id
      bitset.set(bitset.get() or bit)
      values[id].set(value)
      return this
    }

    fun isSet(id: Int): Boolean {
      val bit = 1 shl id
      return bitset.get() and bit != 0
    }

    operator fun get(id: Int) = values[id].get()

    fun getOrDefault(id: Int, defaultValue: Int) = if (isSet(id)) get(id) else defaultValue

    fun size() = Integer.bitCount(bitset.get())

    fun count() = values.size

    fun merge(settings: Frame.Settings) {

    }

    companion object {
      val HEADER_TABLE_SIZE: Int = 0x01
      val ENABLE_PUSH: Int = 0x02
      val MAX_CONCURRENT_STREAMS: Int = 0x03
      val INITIAL_WINDOW_SIZE: Int = 0x04
      val MAX_FRAME_SIZE: Int = 0x05
      val MAX_HEADER_LIST_SIZE: Int = 0x06
    }

  }

}
