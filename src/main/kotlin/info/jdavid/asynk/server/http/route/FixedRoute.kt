package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.Uri
import info.jdavid.asynk.server.http.handler.HttpHandler

/**
 * Simple [Route] implementation that only accepts one uri (but the uri can be relative and match several
 * absolute uris).
 * @param path the accepted path ("/test" for instance).
 * @param methods the list of accepted request methods (HEAD or GET by default).
 */
class FixedRoute(
  path: String,
  private val methods: List<Method> = listOf(Method.GET, Method.HEAD)): HttpHandler.Route<NoParams> {
  private val path = validate(path)
  private val relative = path[0] != '/'

  override fun match(method: Method, uri: String): NoParams? {
    if (methods.contains(method)) {
      if (relative) {
        if (Uri.path(uri).let {
            val index = it.indexOf(path)
            index > -1 && path.length + index == it.length
          }) return NoParams
      }
      else if (Uri.path(uri) == path) return NoParams
    }
    return null
  }

  private companion object {
    fun validate(path: String) =
      if (path.isEmpty()) throw IllegalArgumentException("Path is empty.") else path
  }

}
