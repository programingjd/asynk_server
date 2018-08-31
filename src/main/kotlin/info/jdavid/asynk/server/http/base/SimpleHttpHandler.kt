@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server.http.base

import info.jdavid.asynk.server.http.Method

abstract class SimpleHttpHandler: AbstractHttpHandler<SimpleHttpHandler.Acceptance,
                                                      AbstractHttpHandler.Context>() {

  override suspend fun context(others: Collection<*>?) = Context(others)

  override suspend fun acceptUri(method: Method, uri: String): Acceptance? {
    return when (method) {
      Method.OPTIONS -> Acceptance(
        false, false, method, uri)
      Method.HEAD -> Acceptance(false,
                                false,
                                method,
                                uri)
      Method.GET -> Acceptance(false,
                               false,
                               method,
                               uri)
      Method.POST -> Acceptance(true,
                                true,
                                method,
                                uri)
      Method.PUT -> Acceptance(true,
                               true,
                               method,
                               uri)
      Method.DELETE -> Acceptance(true,
                                  false,
                                  method,
                                  uri)
      Method.PATCH -> Acceptance(true,
                                 true,
                                 method,
                                 uri)
      else -> Acceptance(true, false, method, uri)
    }
  }

  open class Acceptance(bodyAllowed: Boolean, bodyRequired: Boolean,
                        val method: Method, val uri: String): info.jdavid.asynk.server.http.Acceptance(bodyAllowed, bodyRequired)

}
