package info.jdavid.server.http

class Uri private constructor() {

  companion object {

    

    internal fun path(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      return if (end == -1) uri else uri.substring(0, end)
    }

    internal fun parent(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(0, slash + 1) else uri.substring(0, slash + 1) + uri.substring(end)
    }

    internal fun lastPathSegment(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(slash + 1) else uri.substring(slash + 1, end)
    }

  }

}
