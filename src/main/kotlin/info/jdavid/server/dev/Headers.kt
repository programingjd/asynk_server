package info.jdavid.server.dev


class Headers(internal val lines: MutableList<String> = ArrayList(16)) {

  internal fun add(line: String): Headers {
    lines.add(line)
    return this
  }

  fun add(name: String, value: String): Headers {
    lines.add("${name}: ${value}")
    return this
  }

  fun value(name: String): String? {
    val lower = name.toLowerCase()
    return lines.findLast { matches(it, lower) }?.substring(name.length + 1)?.trim()
  }

  fun values(name: String): List<String> {
    val lower = name.toLowerCase()
    return lines.filter { matches(it, lower) }.map { it.substring(name.length + 1).trim() }
  }

  fun keys(): List<String> {
    val set = HashSet<String>(lines.size)
    val list = ArrayList<String>(lines.size)
    lines.forEach {
      val key = it.substring(0, it.indexOf(':'))
      if (set.add(key.toLowerCase())) list.add(key)
    }
    return list
  }

  fun has(name: String): Boolean {
    val lower = name.toLowerCase()
    return lines.find { matches(it, lower) } != null
  }

  companion object {
    val WWW_AUTHENTICATE = "WWW-Authenticate"
    val AUTHORIZATION = "Authorization"

    val AGE = "Age"
    val CACHE_CONTROL = "Cache-Control"
    val EXPIRES = "Expires"
    val PRAGMA = "Pragma"
    val WARNING = "Warning"

    val LAST_MODIFIED = "Last-Modified"
    val ETAG = "ETag"
    val IF_MATCH = "If-Match"
    val IF_NONE_MATCH = "If-None-Match"
    val IF_MODIFIED_SINCE = "If-Modified-Since"
    val IF_UNMODIFIED_SINCE = "If-Unmodified-Since"

    val CONNECTION = "Connection"
    val KEEP_ALIVE = "Keep-Alive"

    val ACCEPT = "Accept"
    val ACCEPT_CHARSET = "Accept-Charset"
    val ACCEPT_ENCODING = "Accept-Encoding"
    val ACCEPT_LANGUAGE = "Accept-Language"
    val ACCEPT_PATCH = "Accept-Patch"

    val EXPECT = "Expect"

    val COOKIE = "Cookie"
    val SET_COOKIE = "Set-Cookie"

    val ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
    val ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials"
    val ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers"
    val ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods"
    val ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers"
    val ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age"
    val ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers"
    val ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method"
    val ORIGIN = "Origin"
    val TIMING_ALLOW_ORIGIN = "Timing-Allow-Origin"

    val DO_NOT_TRACK = "DNT"
    val DNT = DO_NOT_TRACK
    val TRACKING_STATUS = "Tk"
    val Tk = TRACKING_STATUS

    val CONTENT_DISPOSITION = "Content-Disposition"

    val CONTENT_LENGTH = "Content-Length"
    val CONTENT_TYPE = "Content-Type"
    val CONTENT_ENCODING = "Content-Encoding"
    val CONTENT_LANGUAGE = "Content-Language"
    val CONTENT_LOCATION = "Content-Location"

    val FORWARDED = "Forwarded"
    val X_FORWARDED_WITH = "X-Forwarded-With"
    val X_FORWARDED_FOR = "X-Forwarded-For"
    val X_FORWARDED_HOST = "X-Forwarded-Host"
    val X_FORWARDED_PROTO = "X-Forwarded-Proto"
    val VIA = "Via"

    val LOCATION = "Location"

    val FROM = "From"
    val HOST = "Host"
    val REFERER = "Referer"
    val REFERRER = REFERER
    val REFERRER_POLICY = "Referrer-Policy"
    val USER_AGENT = "User-Agent"

    val ALLOW = "Allow"
    val SERVER = "Server"

    val ACCEPT_RANGES = "Accept-Ranges"
    val RANGE = "Range"
    val IF_RANGE = "If-Range"
    val CONTENT_RANGE = "Content-Range"

    val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
    val CSP = CONTENT_SECURITY_POLICY
    val PUBLIC_KEY_PINS = "Public-Key-Pins"
    val HPKP = PUBLIC_KEY_PINS
    val STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security"
    val HSTS = STRICT_TRANSPORT_SECURITY
    val UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests"
    val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    val X_FRAME_OPTIONS = "X-Frame-Options"
    val X_XSS_PROTECTION = "X-XSS-Protection"

    val TRANSFER_ENCODING = "Transfer-Encoding"
    val TE = "TE"
    val TRAILER = "Trailer"

    val DATE = "Date"
    val LARGE_ALLOCATION = "Large-Allocation"
    val LINK = "Link"
    val RETRY_AFTER = "Retry-After"
    val SOURCE_MAP = "SourceMap"
    val UPGRADE = "Upgrade"
    val VARY = "Vary"
    val X_DNS_PREFETCH_CONTROL = "X-DNS-Prefetch-Control"

    private fun matches(line: String, lowercaseName: String): Boolean {
      return line.length > lowercaseName.length + 1 &&
        line.substring(0, lowercaseName.length).toLowerCase() == lowercaseName &&
        line[lowercaseName.length] == ':'
    }
  }

}
