package info.jdavid.server.http.route

import info.jdavid.server.http.Method
import info.jdavid.server.http.handler.HttpHandler

object NoParams: HttpHandler.Route<NoParams> {

  override fun match(method: Method, uri: String): NoParams? {
    return this
  }

}
