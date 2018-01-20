package info.jdavid.server.http

import java.text.ParseException
import java.util.regex.Pattern

/**
 * /some/path/with/{param1}/and/{param2}
 */
class ParameterizedRoute private constructor(
  private val methods: List<Method>,
  private val compiled: Pair<Pattern,List<String>>): HttpHandler.Route<Map<String, String>> {
  constructor(path: String,
              methods: List<Method> = listOf(Method.GET, Method.HEAD)): this(methods, compile(path))

  override fun match(method: Method, uri: String): Map<String, String>? {
    if (methods.contains(method)) {
      val matcher = compiled.first.matcher(Uri.path(uri))
      if (matcher.find()) {
        return compiled.second.associateBy { matcher.group(it) }
      }
    }
    return null
  }


  private companion object {
    private val parameterPattern = Pattern.compile("[a-zA-Z0-9_]")
    fun compile(path: String): Pair<Pattern, List<String>> {
      val params = mutableListOf<String>()
      val pattern = StringBuilder()
      var end = -1
      while (true) {
        val start = path.indexOf('{', end + 1)
        if (start == -1) {
          path.substring(end + 1).let {
            if (!it.isEmpty()) pattern.append(Pattern.quote(it))
          }
          break
        }
        if (start > end + 1) {
          pattern.append(Pattern.quote(path.substring(end + 1, start)))
        }
        end = path.indexOf('}', start + 1)
        if (end == -1) throw ParseException("Unclosed parameter definition: ${path}.", start)
        if (end == start + 1) throw ParseException("Missing parameter name: ${path}.", end)
        path.substring(start+1, end).let {
          if (!parameterPattern.matcher(it).matches()) {
            throw ParseException("Invalid paramter name: ${path}.", start + 1)
          }
          params.add(it)
          pattern.append("(?<${it}>[^/]+)")
        }
      }
      return Pattern.compile("^${pattern}$") to params
    }
  }

}
