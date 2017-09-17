package info.jdavid.server.http.handler

import info.jdavid.server.Uri
import java.util.*
import java.util.regex.Pattern

abstract class RegexHandler(private val methods: Collection<String>, regex: String): Handler {
  constructor(method: String, regex: String): this(Collections.singletonList(method), regex)

  private val pattern: Pattern = Pattern.compile(regex)

  override fun setup() = this

  override fun matches(method: String, uri: String): Array<String>? {
    if (methods.contains(method)) {
      val path = Uri.path(uri)
      val matcher = pattern.matcher(path)
      if (matcher.find()) {
        if (matcher.start() > 0) return null
        if (matcher.end() < path.length) return null
        return Array(matcher.groupCount(), { matcher.group(it + 1) })
      }
    }
    return null
  }



}
