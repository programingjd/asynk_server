package info.jdavid.server.http.http2

internal class Stream(val id: Int) {
  var state = State.IDLE

  enum class State {
    IDLE, OPEN, RESERVED, HALF_CLOSED, CLOSED
  }

}
