package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.handler.HttpHandler

object NoParams: HttpHandler.Route<NoParams>, Map<String, String> {

  override fun match(method: Method, uri: String): NoParams? {
    return this
  }

  override val entries = emptySet<Map.Entry<String,String>>()

  override val keys = emptySet<String>()

  override val values = emptySet<String>()

  override val size = 0

  override fun isEmpty() = true

  override fun containsKey(key: String) = false

  override fun containsValue(value: String) = false

  override fun get(key: String) = null

}
