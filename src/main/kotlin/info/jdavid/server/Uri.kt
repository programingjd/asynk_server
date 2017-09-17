package info.jdavid.server

class Uri private constructor() {


  companion object {

    fun scheme(uri: String): String {
      val index = uri.indexOf(':')
      if (index == -1 || index > uri.length - 4 ||
          uri[index+1] != '/' || uri[index+2] != '/') throw IllegalArgumentException("Invalid uri: ${uri}")
      return uri.substring(0, index)
    }

    fun authority(uri: String): String {
      val index = uri.indexOf('/')
      if (index == -1 || index > uri.length - 3 ||
          uri[index+1] != '/') throw IllegalArgumentException("Invalid uri: ${uri}")
      val end = uri.indexOf('/', index + 2)
      return if (end == -1) uri.substring(index + 2) else uri.substring(index + 2, end)
    }

    fun path(uri: String): String {
      val index = uri.indexOf('/')
      if (index == -1 || index > uri.length - 3 ||
          uri[index+1] != '/') throw IllegalArgumentException("Invalid uri: ${uri}")
      val start = uri.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = uri.indexOf('?', start + 1)
      val end = if (stop == -1) uri.indexOf('#', start + 1) else stop
      return if (end == -1) uri.substring(start) else uri.substring(start, end)
    }

    internal fun https(uri: String): String {
      val scheme = uri.indexOf(':')
      val index = uri.indexOf('/')
      val end = uri.indexOf('/', index + 2)
      val port = if (end == -1) uri.lastIndexOf(':') else uri.lastIndexOf(':', end)
      return if (port != scheme) {
        val n = (if (end == -1) uri.substring(port + 1) else uri.substring(port + 1, end)).toInt()
        if (n == 80) {
          if (end == -1) {
            "https" + uri.substring(scheme, port) + ":443"
          }
          else {
            "https" + uri.substring(scheme, port) + ":443" + uri.substring(end)
          }
        }
        else if (n == 8080) {
          if (end == -1) {
            "https" + uri.substring(scheme, port) + ":8181"
          }
          else {
            "https" + uri.substring(scheme, port) + ":8181" + uri.substring(end)
          }
        }
        else {
          if (end == -1) {
            "https" + uri.substring(scheme, port)
          }
          else {
            "https" + uri.substring(scheme, port) + uri.substring(end)
          }
        }
      }
      else {
        "https" + uri.substring(scheme)
      }
    }

    internal fun parent(uri: String): String {
      val index = uri.indexOf('/')
      val start = uri.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = uri.indexOf('?', start + 1)
      val end = if (stop == -1) uri.indexOf('#', start + 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(0, slash + 1) else uri.substring(0, slash + 1) + uri.substring(end)
    }

    internal fun lastPathSegment(uri: String): String {
      val index = uri.indexOf('/')
      val start = uri.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = uri.indexOf('?', start + 1)
      val end = if (stop == -1) uri.indexOf('#', start + 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(slash + 1) else uri.substring(slash + 1, end)
    }

  }

}
