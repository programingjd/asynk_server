package info.jdavid.server.http.http2

import info.jdavid.server.SocketConnection
import info.jdavid.server.Connection
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.experimental.CoroutineContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Suppress("UsePropertyAccessSyntax")
internal class Http2Connection(bufferPool: LockFreeLinkedListHead,
                               maxRequestSize: Int,
                               val context: CoroutineContext,
                               val socketConnection: SocketConnection,
                               val readTimeoutMillis: Long,
                               val writeTimeoutMillis: Long): Connection(bufferPool, maxRequestSize) {
  private val hpack = HPack()
  private val settings = Settings().
    set(Settings.HEADER_TABLE_SIZE, hpack.getMaxSize()).
    set(Settings.MAX_CONCURRENT_STREAMS, 128).
    set(Settings.INITIAL_WINDOW_SIZE, 65535).
    set(Settings.MAX_FRAME_SIZE, 16384)

  private val streams = mutableMapOf<Int, Stream>()

  suspend fun start(): Http2Connection {
    val writeDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
    val readDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)

    streams.put(0, connectionPreface(readDeadline, writeDeadline))
    return this
  }

  suspend fun stream(readTimeoutMillis: Long, writeTimeoutMillis: Long) {
    val writeDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis)
    val readDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis)

    val headers: Frame.Headers =
      Frame.read(
        socketConnection,
        readDeadline
      ) as? Frame.Headers ?: throw ConnectionException.ProtocolError()
    val stream = (streams[0] ?: throw NullPointerException())
    stream.unpackRequestHeaders(headers)

    if (!headers.endHeaders) {
      while (true) {
        val continuation =
          Frame.read(
            socketConnection,
            readDeadline
          ) as? Frame.Continuation ?: throw ConnectionException.ProtocolError()
        if (stream.id != headers.streamId) throw ConnectionException.ProtocolError()
        stream.unpackRequestHeaders(continuation)
        if (continuation.endHeaders) break
      }
    }

  }

  suspend override fun close() {
    TODO("close all streams, recycle their buffers")
  }

  suspend private fun connectionPreface(readDeadline: Long, writeDeadline: Long): Stream {
    val buffers = buffers()
    // Client should send 0x505249202a20485454502f322e300d0a0d0a534d0d0a0d0a
    // (PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n)
    // followed by a SETTINGS frame.
    // Server should send a SETTINGS frame and acknowledge the client SETTINGS frame.
    val clientSettings = async(context) {
      val connectionPreface = socketConnection.read(readDeadline, CONNECTION_PREFACE.size)
      @Suppress("LoopToCallChain")
      for (i in 0 until CONNECTION_PREFACE.size) {
        if (connectionPreface[i] != CONNECTION_PREFACE[i]) {
          throw IOException("Invalid connection preface.")
        }
      }
      val settings =
        Frame.read(
          socketConnection,
          readDeadline
        ) as? Frame.Settings ?: throw ConnectionException.ProtocolError()
      if (settings.ack) throw ConnectionException.ProtocolError() else settings
    }
    val server = async(context) {
      Frame.write(
        socketConnection,
        writeDeadline,
        Frame.Settings(0, 0, settings.write(buffers.buffer))
      )
    }
    // wait for server SETTINGS frame to be sent
    server.await()
    // wait for client SETTINGS frame to be received
    settings.merge(clientSettings.await())
    // send ACK
    Frame.write(
      socketConnection,
      writeDeadline,
      Frame.Settings(0, 0x01, null)
    )
    return Stream(0, buffers)
  }

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
      val payload = settings.payload
      if (payload != null) {
        val length = payload.remaining()
        for (i in 0 until length) {
          val id = payload.getShort().toInt()
          val value = payload.getInt()
          if (isSet(id)) {
            when (id) {
              HEADER_TABLE_SIZE,
              MAX_CONCURRENT_STREAMS,
              INITIAL_WINDOW_SIZE,
              MAX_FRAME_SIZE,
              MAX_HEADER_LIST_SIZE -> if (get(id) > value) set(id, value)
              else -> set(id, value)
            }
          }
          else {
            set(id, value)
          }
        }
      }
    }

    fun write(segment: ByteBuffer): ByteBuffer {
      segment.rewind().limit(segment.capacity())
      for (i in 1..count()) {
        if (isSet(i)) {
          segment.putShort(i.toShort())
          segment.putInt(get(i))
        }
      }
      return segment.flip()
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
