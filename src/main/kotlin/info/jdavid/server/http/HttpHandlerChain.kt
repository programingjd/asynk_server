@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server.http

import info.jdavid.server.Handler
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

internal class HttpHandlerChain(val chain: List<HttpHandler<out Handler.Acceptance>>):
  HttpHandler<HttpHandlerChain.HandlerAcceptance<out Handler.Acceptance>>() {

  private val context = ChainContext(chain.associate { it to it.context() })

  override fun context() = context

  suspend override fun acceptUri(method: Method, uri: String): HandlerAcceptance<out Handler.Acceptance>? {
    for (handler in chain) {
      val acceptance = handler.acceptUri(method, uri)
      if (acceptance != null) return HandlerAcceptance(handler, acceptance)
    }
    return null
  }

  suspend override fun handle(acceptance: HandlerAcceptance<out Handler.Acceptance>,
                              headers: Headers,
                              body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: Any?) {
    acceptance.handle(headers, body, socket, context)
  }

  internal class HandlerAcceptance<T: Handler.Acceptance>(
    private val handler: HttpHandler<T>,
    private val acceptance: Handler.Acceptance): Handler.Acceptance {
    override val bodyAllowed: Boolean
      get() = acceptance.bodyAllowed
    override val bodyRequired: Boolean
      get() = acceptance.bodyRequired

    suspend fun handle(headers: Headers,
                       body: ByteBuffer,
                       socket: AsynchronousSocketChannel,
                       context: Any?) {
      @Suppress("UNCHECKED_CAST")
      handler.handle(acceptance as T, headers, body, socket, (context as ChainContext).contexts[handler])
    }
  }

  internal class ChainContext(val contexts: Map<HttpHandler<out Handler.Acceptance>, Context>): Context()

}
