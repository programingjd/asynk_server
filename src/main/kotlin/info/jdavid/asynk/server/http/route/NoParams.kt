package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.handler.HttpHandler

object NoParams: HttpHandler.Route<NoParams> {

  override fun match(method: Method, uri: String): NoParams? {
    return this
  }

}
