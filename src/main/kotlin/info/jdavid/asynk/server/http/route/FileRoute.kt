package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.http.handler.HttpHandler
import java.io.File
import java.net.URLDecoder

/**
 * File-based [Route] implementation.<br>
 * Accepts uris of the form:<br>
 *   prefix/root_directory<br>
 *   prefix/root_directory/sub_directory<br>
 *   prefix/root_directory/sub_directory/file<br>
 * The default prefix is simply "/".
 * Only GET and HEAD requests are accepted.
 * @param root the root directory.
 * @param prefix the prefix.
 */
open class FileRoute(private val root: File, private val prefix: String = "/"): HttpHandler.Route<File> {
  init {
    if (root.isFile) throw IllegalArgumentException()
    if (prefix.isEmpty() || prefix[0] != '/') throw IllegalArgumentException()
  }
  final override fun match(method: Method, uri: String): File? {
    if (method == Method.GET || method == Method.HEAD && uri.startsWith(prefix)) {
      val segments = Uri.path(uri).substring(prefix.length).split('/')
      var file = root
      for (segment in segments) {
        if (segment.isEmpty()) {
          if (file.isFile) return null
        }
        else file = File(file, URLDecoder.decode(segment, "UTF-8"))
      }
      return if (file.exists()) file else null
    }
    else return null
  }

}
