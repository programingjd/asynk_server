package info.jdavid.server.http.handler

import info.jdavid.server.SocketConnection
import info.jdavid.server.Uri
import info.jdavid.server.http.MediaTypes
import info.jdavid.server.http.Statuses
import info.jdavid.server.http.http11.Headers
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer

open class FileHandler(regex: String,
                       private val webRoot: File,
                       indexNames: List<String> = DEFAULT_INDEX_NAMES): RegexHandler(METHODS, regex) {
  constructor(webRoot: File, indexNames: List<String>): this("(.*)", webRoot, indexNames)
  constructor(webRoot: File): this("(.*)", webRoot)

  private val indexNames = indexNames.asSequence()

  override fun setup(): FileHandler {
    super.setup()

    return this
  }

  suspend override fun handle(method: String, uri: String, headers: Headers,
                              socketConnection: SocketConnection, deadline: Long,
                              buffer: ByteBuffer, params: Array<String>): Handler.Response {
    val n = params.size
    if (n < 1) return Handler.Response(Statuses.INTERNAL_SERVER_ERROR)
    val path = params[n-1]
    val file = File(webRoot.toURI().resolve(".${path}"))
    if (file.isDirectory) {
      val pathLength = path.length
      if (pathLength > 0 && path[pathLength - 1] != '/') {
        index(file) ?: return Handler.Response(Statuses.FORBIDDEN)
        return Handler.Response(Statuses.MOVED_PERMANENTLY, Headers().add(Headers.LOCATION, "${uri}/"))
      }
    }
    if (isIndexFile(file)) {
      return Handler.Response(Statuses.MOVED_PERMANENTLY, Headers().add(Headers.LOCATION, Uri.parent(uri)))
    }
    val mediaType = mediaType(file) ?: return Handler.Response(Statuses.NOT_FOUND)
    val m: String
    val f: File
    if (mediaType == MediaTypes.DIRECTORY) {
      f = index(file) ?: return Handler.Response(Statuses.NOT_FOUND)
      m = mediaType(f) ?: return Handler.Response(Statuses.NOT_FOUND)
    }
    else {
      f = file
      m = mediaType
    }

    if (f.exists()) {
      try {
        val config = config(mediaType)
        val compress = config.compress
        val gzip = compress && headers.value(Headers.ACCEPT_ENCODING) == GZIP

        return Handler.Response(Statuses.OK, Headers().add(Headers.CONTENT_LENGTH, "${file.length()}"),
                                file.readBytes())

      }
      catch (e: FileNotFoundException) {
        return Handler.Response(Statuses.NOT_FOUND)
      }
    }
    else {
      return Handler.Response(Statuses.NOT_FOUND)
    }
  }

  protected fun config(mediaType: String): MediaTypeConfig {
    return when (mediaType) {
      MediaTypes.HTML, MediaTypes.XHTML, MediaTypes.WEB_MANIFEST ->
        MediaTypeConfig(true, false, false, htmlMaxAge())
      MediaTypes.CSS ->
        MediaTypeConfig(true, false,false, cssMaxAge())
      MediaTypes.JAVASCRIPT ->
        MediaTypeConfig(true, false, false, jsMaxAge())
      MediaTypes.WOFF, MediaTypes.WOFF2, MediaTypes.EOT ->
        MediaTypeConfig(false, false, true, 31536000) // one year
      MediaTypes.OTF, MediaTypes.TTF ->
        MediaTypeConfig(true, false, true, 31536000) // one year
      MediaTypes.JSON, MediaTypes.XML, MediaTypes.ATOM, MediaTypes.CSV ->
        MediaTypeConfig(true, false, true, smallFileMaxAge()) // one year
      MediaTypes.GZIP, MediaTypes.TAR, MediaTypes.XZ, MediaTypes.SEVENZ, MediaTypes.ZIP,
      MediaTypes.OCTET_STREAM, MediaTypes.PDF ->
        MediaTypeConfig(false, true, false, largeFileMaxAge())
      else -> {
        if (mediaType.startsWith("image")) {
          MediaTypeConfig(false, false, true, imageMaxAge())
        }
        else if (mediaType.startsWith("video") || mediaType.startsWith("audio")) {
          MediaTypeConfig(false, true, false, largeFileMaxAge())
        }
        else {
          MediaTypeConfig(mediaType.startsWith("text"), false, false, 0)
        }
      }
    }
  }


  protected fun imageMaxAge() = 31536000 // one year

  protected fun largeFileMaxAge() = 0 // always re-validate

  protected fun smallFileMaxAge() = -1 // don't cache

  protected fun cssMaxAge() = 0 // always re-validate

  protected fun jsMaxAge() = 0 // always re-validate

  protected fun htmlMaxAge() = 0 // always re-validate

  private fun index(file: File): File? {
    return indexNames
      .map { File(file, it) }
      .firstOrNull { it.exists() }
  }

  private fun isIndexFile(file: File): Boolean {
    val filename = file.name
    if (file.isFile && indexNames.contains(filename)) {
      val index = index(file.parentFile)
      if (index != null && index.name == filename) return true
    }
    return false
  }

  private fun mediaType(file: File): String? {
    return MediaTypes.fromFile(file)
  }

  protected class MediaTypeConfig(internal val compress: Boolean, internal val ranges: Boolean,
                                  internal val immutable: Boolean, internal val maxAge: Int)

  private companion object {
    val METHODS = listOf("GET", "HEAD")
    val DEFAULT_INDEX_NAMES = listOf("index.html", "index.htm")
    val GZIP = "gzip"
  }

}
