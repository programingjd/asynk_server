@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.NoParams
import java.nio.ByteBuffer

internal class HttpHandlerChain(
  private val chain: List<HttpHandler<*,*,*,*>>
): HttpHandler<HttpHandler.Acceptance<Any>, Any, HttpHandlerChain.ChainContext, Any>(NoParams) {

  override suspend fun context(others: Collection<*>?): ChainContext {
    val map = HashMap<HttpHandler<*,*,*,*>, AbstractHttpHandler.Context>(chain.size)
    chain.forEach {
      map[it] = it.context(map.values)
    }
   return ChainContext(others, map)
  }

  override suspend fun acceptUri(method: Method, uri: String,
                                 params: Any): HttpHandler.Acceptance<Any>? {
    for (handler in chain) {
      val acceptance = handler.acceptUri(method, uri) as? Acceptance<Any>
      if (acceptance != null) return HandlerAcceptance(
        handler, acceptance)
    }
    return null
  }

  override suspend fun handle(acceptance: HttpHandler.Acceptance<Any>, headers: Headers,
                              body: ByteBuffer, context: ChainContext) =
    (acceptance as HandlerAcceptance<*,*>).handle(headers, body, context)

  //  override suspend fun handle(acceptance: HandlerAcceptance<out Handler.Acceptance, out Context>,
//                              headers: Headers,
//                              body: ByteBuffer,
//                              socket: AsynchronousSocketChannel,
//                              context: ChainContext) {
//    acceptance.handle(headers, body, socket, context)
//  }

  internal class HandlerAcceptance<ACCEPTANCE: HttpHandler.Acceptance<*>,
                                   CONTEXT: AbstractHttpHandler.Context>(
    private val handler: HttpHandler<ACCEPTANCE, *, CONTEXT, *>,
    private val acceptance: HttpHandler.Acceptance<Any>): Acceptance<Any>(acceptance.bodyAllowed,
                                                                        acceptance.bodyRequired,
                                                                        acceptance.method,
                                                                        acceptance.uri,
                                                                        acceptance.routeParams) {
    suspend fun handle(headers: Headers, body: ByteBuffer, context: ChainContext): Response<*> {
      @Suppress("UNCHECKED_CAST")
      return handler.handle(acceptance as ACCEPTANCE, headers, body, context.contexts[handler] as CONTEXT)
    }
  }

  internal class ChainContext(
    others: Collection<*>?,
    val contexts: Map<HttpHandler<*,*,*,*>, AbstractHttpHandler.Context>
  ): AbstractHttpHandler.Context(others, 4096)

}
