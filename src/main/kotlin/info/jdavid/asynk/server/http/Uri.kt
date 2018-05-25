package info.jdavid.asynk.server.http

class Uri private constructor() {

  companion object {

    fun query(uri: String): Map<String,String>? {
      val qmark = uri.indexOf('?', 1)
      if (qmark == -1) return null
      val hash = uri.indexOf('#', qmark + 1)
      val q = if (hash == -1) uri.substring(qmark + 1) else uri.substring(qmark + 1, hash)
      return q.split('&').let {
        val map = LinkedHashMap<String, String>(it.size)
        it.forEach {
          if (it.isNotEmpty()) {
            it.split('=').apply {
              when (size) {
                0 -> map[""] = ""
                1 -> map[this[0]] = ""
                else -> map[this[0]] = this[1]
              }
            }
          }
        }
        map
      }
    }

    fun fragment(uri: String): String? {
      val hash = uri.indexOf('#', 1)
      return if (hash == -1) null else uri.substring(hash + 1)
    }

    fun path(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      return if (end == -1) uri else uri.substring(0, end)
    }

    fun parent(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(0, slash + 1) else uri.substring(0, slash + 1) + uri.substring(end)
    }

    fun lastPathSegment(uri: String): String {
      val stop = uri.indexOf('?', 1)
      val end = if (stop == -1) uri.indexOf('#', 1) else stop
      val slash = if (end == -1) uri.lastIndexOf('/') else uri.lastIndexOf('/', end - 1)
      return if (end == -1) uri.substring(slash + 1) else uri.substring(slash + 1, end)
    }

  }

}
