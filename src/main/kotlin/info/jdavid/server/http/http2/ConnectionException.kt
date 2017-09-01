package info.jdavid.server.http.http2

sealed class ConnectionException(internal val code: Int): Exception() {
  class ProtocolError: ConnectionException(0x01)
  class InternalError: ConnectionException(0x02)
  class FlowControlError: ConnectionException(0x03)
  class SettingsTimeout: ConnectionException(0x04)
  class StreamClosed: ConnectionException(0x05)
  class FrameSizeError: ConnectionException(0x06)
  class RefusedStream: ConnectionException(0x07)
  class Cancel: ConnectionException(0x08)
  class CompressionError: ConnectionException(0x09)
  class ConnectError: ConnectionException(0x0a)
  class EnhanceYourCalm: ConnectionException(0x0b)
  class InadequateSecurity: ConnectionException(0x0c)
  class http11Required: ConnectionException(0x0d)
  class Unknown(code: Int): ConnectionException(code)
}
