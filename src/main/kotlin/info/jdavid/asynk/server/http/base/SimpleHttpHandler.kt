@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.http.Method
import java.net.InetSocketAddress

abstract class SimpleHttpHandler(private val maxRequestSize: Int = 4096):
  AbstractHttpHandler<SimpleHttpHandler.Acceptance, AbstractHttpHandler.Context>() {

  override suspend fun context(others: Collection<*>?) = Context(others, maxRequestSize)

  override suspend fun acceptUri(remoteAddress: InetSocketAddress, method: Method, uri: String): Acceptance? {
    return when (method) {
      Method.OPTIONS -> Acceptance(
        remoteAddress, false, false, method, uri)
      Method.HEAD -> Acceptance(remoteAddress, false, false, method, uri)
      Method.GET -> Acceptance(remoteAddress, false, false, method, uri)
      Method.POST -> Acceptance(remoteAddress, true, true, method, uri)
      Method.PUT -> Acceptance(remoteAddress, true, true, method, uri)
      Method.DELETE -> Acceptance(remoteAddress, true, false, method, uri)
      Method.PATCH -> Acceptance(remoteAddress, true, true, method, uri)
      else -> Acceptance(remoteAddress, true, false, method, uri)
    }
  }

  /**
   * Acceptance object that stores request method and uri.
   * @param remoteAddress specifies the address of the incoming connection.
   * @param bodyAllowed specifies whether the request is allowed to include incoming data.
   * @param bodyRequired specifies whether the request body when allowed is required or not.
   * @param method the request HTTP method.
   * @param uri the request uri.
   */
  open class Acceptance(remoteAddress: InetSocketAddress,
                        bodyAllowed: Boolean, bodyRequired: Boolean,
                        val method: Method, val uri: String):
                        info.jdavid.asynk.server.http.Acceptance(remoteAddress, bodyAllowed, bodyRequired)

}
