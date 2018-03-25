package info.jdavid.server.http.route

import info.jdavid.server.http.handler.HttpHandler
import info.jdavid.server.http.Method
import info.jdavid.server.http.Uri
import java.io.File
import java.net.URLDecoder

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
