package info.jdavid.server.discarded.http.http2

import info.jdavid.server.discarded.SocketConnection
import java.nio.ByteBuffer

internal abstract class Frame(internal val streamId: Int,
                              private val type: Int,
                              internal val flags: Int,
                              internal val payload: ByteBuffer?) {

  class Data(streamId: Int, flags: Int, payload: ByteBuffer?):
             Frame(streamId, Types.DATA, flags, payload)
  class Headers(streamId: Int, flags: Int, payload: ByteBuffer?):
                Frame(streamId, Types.HEADERS, flags, payload) {
    val pad = flags and Flags.PADDED != 0
    val priority = flags and Flags.PRIORITY != 0
    val endHeaders = flags and Flags.END_HEADERS != 0
    val endStream = flags and Flags.END_STREAM != 0
  }
  class Priority(streamId: Int, flags: Int, payload: ByteBuffer?):
                 Frame(streamId, Types.PRIORITY, flags, payload)
  class RstStream(streamId: Int, flags: Int, payload: ByteBuffer?):
                  Frame(streamId, Types.RST_STREAM, flags, payload)
  class Settings(streamId: Int, flags: Int, payload: ByteBuffer?):
                 Frame(streamId, Types.SETTINGS, flags, payload) {
    val ack = flags and Flags.ACK != 0
    init {
      if (streamId != 0) throw ConnectionException.ProtocolError()
      if (ack && (payload?.position() ?: 0) != 0) throw ConnectionException.FrameSizeError()
      if ((payload?.position() ?: 0) % 6 != 0) throw ConnectionException.FrameSizeError()
    }
  }
  class PushPromise(streamId: Int, flags: Int, payload: ByteBuffer?):
                   Frame(streamId, Types.PUSH_PROMISE, flags, payload)
  class Ping(streamId: Int, flags: Int, payload: ByteBuffer?):
             Frame(streamId, Types.PING, flags, payload)
  class GoAway(streamId: Int, flags: Int, payload: ByteBuffer?):
               Frame(streamId, Types.GOAWAY, flags, payload)
  class WindowUpdate(streamId: Int, flags: Int, payload: ByteBuffer?):
                     Frame(streamId, Types.WINDOW_UPDATE, flags, payload)
  class Continuation(streamId: Int, flags: Int, payload: ByteBuffer?):
                     Frame(streamId, Types.CONTINUATION, flags, payload) {
    val endHeaders = flags and Flags.END_HEADERS != 0
  }
  class Unknown(streamId: Int, type: Int, flags: Int, payload: ByteBuffer?):
                Frame(streamId, type, flags, payload)

  companion object {
    suspend fun read(socketConnection: SocketConnection, deadline: Long): Frame {
      val segment = socketConnection.read(deadline, 9)
      segment.rewind().limit(segment.capacity())
      val length = (segment.get().toInt() and 0xff shl 16) or
        (segment.get().toInt() and 0xff shl 8) or
        (segment.get().toInt() and 0xff)
      val type = segment.get().toInt().and(0xff)
      val flags = segment.get().toInt().and(0xff)
      @Suppress("UsePropertyAccessSyntax")
      val streamId = segment.getInt() and 0x7fffffff // ignore reserved bit
      val payload = if (length == 0) null else socketConnection.read(deadline, length)
      return when (type) {
        Http2Connection.Types.DATA -> Data(streamId, flags, payload)
        Http2Connection.Types.HEADERS -> Headers(streamId, flags, payload)
        Http2Connection.Types.PRIORITY -> Priority(streamId, flags, payload)
        Http2Connection.Types.RST_STREAM -> RstStream(streamId, flags, payload)
        Http2Connection.Types.SETTINGS -> Settings(streamId, flags, payload)
        Http2Connection.Types.PUSH_PROMISE -> PushPromise(streamId, flags, payload)
        Http2Connection.Types.PING -> Ping(streamId, flags, payload)
        Http2Connection.Types.GOAWAY -> GoAway(streamId, flags, payload)
        Http2Connection.Types.WINDOW_UPDATE -> WindowUpdate(streamId, flags, payload)
        Http2Connection.Types.CONTINUATION -> Continuation(streamId, flags, payload)
        else -> Unknown(streamId, type, flags, payload)
      }
    }
    suspend fun write(socketConnection: SocketConnection, deadline: Long, frame: Frame) {
      val segment = socketConnection.segment()
      segment.rewind().limit(segment.capacity())
      val length = frame.payload?.remaining() ?: 0
      segment.put((length.ushr(16) and 0xff).toByte())
      segment.put((length.ushr(8) and 0xff).toByte())
      segment.put((length and 0xff).toByte())
      segment.put((frame.type and 0xff).toByte())
      segment.put((frame.flags and 0xff).toByte())
      segment.putInt(frame.streamId and 0x7fffffff)
      if (frame.payload == null) {
        socketConnection.write(deadline, segment)
      }
      else if (length + 9 <= segment.capacity()) {
        segment.put(frame.payload)
        socketConnection.write(deadline, segment)
      }
      else {
        frame.payload.limit(segment.capacity() - 9)
        segment.put(frame.payload)
        socketConnection.write(deadline, segment)
        segment.rewind().limit(segment.capacity())
        frame.payload.limit(length)
        segment.put(frame.payload)
        socketConnection.write(deadline, segment)
      }
    }
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
      internal val ACK = 0x01
      internal val END_STREAM = 0x01
      internal val END_HEADERS = 0x04
      internal val PADDED = 0x08
      internal val PRIORITY = 0x20
    }

  }

}
