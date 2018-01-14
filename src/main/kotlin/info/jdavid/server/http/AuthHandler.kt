package info.jdavid.server.http

import info.jdavid.server.Handler
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

abstract class AuthHandler<T: Handler.Acceptance>(
  private val delegate: AbstractHttpHandler<out T>
): AbstractHttpHandler<T>() {

  suspend override fun acceptUri(method: Method, uri: String): T? {
    return delegate.acceptUri(method, uri)
  }

  suspend override fun handle(acceptance: T,
                              headers: Headers,
                              body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: Any?) {

  }

}
