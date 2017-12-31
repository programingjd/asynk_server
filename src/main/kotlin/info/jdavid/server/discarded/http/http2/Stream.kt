package info.jdavid.server.discarded.http.http2

import com.sun.net.httpserver.Headers
import info.jdavid.server.discarded.Connection
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode

internal class Stream(val id: Int, val buffers: Connection.Buffers): LockFreeLinkedListNode() {
  var state = State.IDLE

  var method: String? = null
  var uri: String? = null
  var headers: Headers? = null

  enum class State {
    IDLE, OPEN, RESERVED, HALF_CLOSED, CLOSED
  }

  fun unpackRequestHeaders(frame: Frame.Headers) {
    if (state === State.CLOSED) throw ConnectionException.StreamClosed()
    if (headers != null) throw ConnectionException.ProtocolError()
    if (state === State.IDLE) state = State.OPEN

    val payload = frame.payload ?: throw ConnectionException.ProtocolError()
    val padding = if (frame.pad) payload.get().toInt() else 0

    @Suppress("UsePropertyAccessSyntax")
    val v = payload.getInt()
    val streamId = v and 0x7fffffff
    val exclusive = frame.priority && v and 0x10000000 != 0
    val weight = if (frame.priority) payload.get().toInt() else 0


  }

  fun unpackRequestHeaders(frame: Frame.Continuation) {

  }

}
