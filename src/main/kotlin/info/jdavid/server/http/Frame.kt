package info.jdavid.server.http

import info.jdavid.server.Channel
import java.nio.ByteBuffer

internal abstract class Frame(internal val streamId: Int,
                              private val type: Int,
                              internal val flags: Int,
                              internal val payload: ByteBuffer) {

  internal class Data(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.DATA, flags, payload)
  internal class Headers(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.HEADERS, flags, payload)
  internal class Priority(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.PRIORITY, flags, payload)
  internal class RstStream(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.RST_STREAM, flags, payload)
  internal class Settings(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.SETTINGS, flags, payload)
  internal class PushPromise(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.PUSH_PROMISE, flags, payload)
  internal class Ping(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.PING, flags, payload)
  internal class GoAway(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.GOAWAY, flags, payload)
  internal class WindowUpdate(streamId: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, Types.WINDOW_UPDATE, flags, payload)
  internal class Unknown(streamId: Int, type: Int, flags: Int, payload: ByteBuffer):
                 Frame(streamId, type, flags, payload)

  internal companion object {
    suspend fun frame(channel: Channel, readDeadline: Long): Frame {
      val segment1 = channel.read(9, readDeadline)
      segment1.rewind().limit(segment1.capacity())
      val length = (segment1.get().toInt() and 0xff shl 16) or
        (segment1.get().toInt() and 0xff shl 8) or
        (segment1.get().toInt() and 0xff)
      val type = segment1.get().toInt().and(0xff)
      val flags = segment1.get().toInt().and(0xff)
      @Suppress("UsePropertyAccessSyntax")
      val streamId = segment1.getInt() and 0x7fffffff // ignore reserved bit
      val segment2 = channel.read(length, readDeadline)
      return when (type) {
        Http2Connection.Types.DATA -> Data(streamId, flags, segment2)
        Http2Connection.Types.HEADERS -> Headers(streamId, flags, segment2)
        Http2Connection.Types.PRIORITY -> Priority(streamId, flags, segment2)
        Http2Connection.Types.RST_STREAM -> RstStream(streamId, flags, segment2)
        Http2Connection.Types.SETTINGS -> Settings(streamId, flags, segment2)
        Http2Connection.Types.PUSH_PROMISE -> PushPromise(streamId, flags, segment2)
        Http2Connection.Types.PING -> Ping(streamId, flags, segment2)
        Http2Connection.Types.GOAWAY -> GoAway(streamId, flags, segment2)
        Http2Connection.Types.WINDOW_UPDATE -> WindowUpdate(streamId, flags, segment2)
        else -> Unknown(streamId, type, flags, segment2)
      }
    }
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

}
