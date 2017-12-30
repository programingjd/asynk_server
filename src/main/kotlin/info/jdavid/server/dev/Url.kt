package info.jdavid.server.dev

class Url private constructor() {

  companion object {

    fun scheme(url: String): String {
      val index = url.indexOf(':')
      if (index == -1 || index > url.length - 4 ||
          url[index + 1] != '/' || url[index + 2] != '/') throw IllegalArgumentException("Invalid url: ${url}")
      return url.substring(0, index)
    }

    fun authority(url: String): String {
      val index = url.indexOf('/')
      if (index == -1 || index > url.length - 3 ||
          url[index + 1] != '/') throw IllegalArgumentException("Invalid url: ${url}")
      val end = url.indexOf('/', index + 2)
      return if (end == -1) url.substring(index + 2) else url.substring(index + 2, end)
    }

    fun path(url: String): String {
      val index = url.indexOf('/')
      if (index == -1 || index > url.length - 3 ||
          url[index + 1] != '/') throw IllegalArgumentException("Invalid url: ${url}")
      val start = url.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = url.indexOf('?', start + 1)
      val end = if (stop == -1) url.indexOf('#', start + 1) else stop
      return if (end == -1) url.substring(start) else url.substring(start, end)
    }

    fun pathSegments(url: String): List<String> {
      val index = url.indexOf('/')
      if (index == -1 || index > url.length - 3 ||
        url[index + 1] != '/') throw IllegalArgumentException("Invalid url: ${url}")
      val start = url.indexOf('/', index + 2)
      if (start == -1) return emptyList()
      val stop = url.indexOf('?', start + 1)
      val end = if (stop == -1) url.indexOf('#', start + 1) else stop
      val last = if (end == -1) url.length else end
      if (last == start + 1) return emptyList()
      val path = if (url[last - 1] == '/') {
        url.substring(start + 1, last - 1)
      }
      else {
        url.substring(start + 1, last)
      }
      return path.split('/')
    }

    internal fun parent(url: String): String {
      val index = url.indexOf('/')
      val start = url.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = url.indexOf('?', start + 1)
      val end = if (stop == -1) url.indexOf('#', start + 1) else stop
      val slash = if (end == -1) url.lastIndexOf('/') else url.lastIndexOf('/', end - 1)
      return if (end == -1) url.substring(0, slash + 1) else url.substring(0, slash + 1) + url.substring(end)
    }

    internal fun lastPathSegment(url: String): String {
      val index = url.indexOf('/')
      val start = url.indexOf('/', index + 2)
      if (start == -1) return ""
      val stop = url.indexOf('?', start + 1)
      val end = if (stop == -1) url.indexOf('#', start + 1) else stop
      val slash = if (end == -1) url.lastIndexOf('/') else url.lastIndexOf('/', end - 1)
      return if (end == -1) url.substring(slash + 1) else url.substring(slash + 1, end)
    }

  }

}
