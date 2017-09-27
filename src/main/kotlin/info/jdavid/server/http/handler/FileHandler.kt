package info.jdavid.server.http.handler

import info.jdavid.server.Base64
import info.jdavid.server.SocketConnection
import info.jdavid.server.http.Uri
import info.jdavid.server.http.MediaTypes
import info.jdavid.server.http.Statuses
import info.jdavid.server.http.http11.Headers
import kotlinx.coroutines.experimental.nio.aRead
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption

open class FileHandler(regex: String,
                       protected val webRoot: File,
                       indexNames: List<String> = DEFAULT_INDEX_NAMES): RegexHandler(METHODS, regex) {
  constructor(webRoot: File, indexNames: List<String>): this("(.*)", webRoot, indexNames)
  constructor(webRoot: File): this("(.*)", webRoot)

  private val indexNames = indexNames.asSequence()
  private val allowedMediaTypes = mutableSetOf<String>()
  private var compress = false

  override fun setup(): FileHandler {
    super.setup()
    allowedMediaTypes.addAll(allowedMediaTypes())
    compress = compress()
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
      if (!isAllowed(mediaType)) return Handler.Response(Statuses.FORBIDDEN)
      m = mediaType
    }
    val etag = etag(f)
    if (etag != null && etag == headers.value(Headers.IF_NONE_MATCH)) {
      return Handler.Response(Statuses.NOT_MODIFIED)
    }
    if (f.exists()) {
      try {
        val config = config(mediaType)
        val compress = this.compress && config.compress
        val gzip = compress && headers.value(Headers.ACCEPT_ENCODING) == GZIP
        val h = Headers()
        when (config.maxAge) {
          -1 -> h.add(Headers.CACHE_CONTROL, "no-store")
          0  -> {
            h.add(Headers.CACHE_CONTROL, "no-cache")
            if (etag != null) h.add(Headers.ETAG, etag)
          }
          else -> {
            h.add(Headers.CACHE_CONTROL, "max-age=" + config.maxAge +
              (if (config.immutable) ", immutable" else ", must-revalidate"))
            if (etag != null) h.add(Headers.ETAG, etag)
          }
        }
        val fileLength = f.length()
        if (config.ranges) {
          h.add(Headers.ACCEPT_RANGES, "bytes")
          val rangeHeaderValue = headers.value(Headers.RANGE)
          if (rangeHeaderValue == null) {
            return fileResponse(f, fileLength, h)
          }
          else {
            if (!rangeHeaderValue.startsWith("bytes")) return Handler.Response(Statuses.BAD_REQUEST)
            if (etag != null) {
              val ifRange = headers.value(Headers.IF_RANGE)
              if (ifRange != null) {
                val firstQuote = ifRange.indexOf('"')
                val lastQuote = ifRange.lastIndexOf('"')
                if (firstQuote != -1 && lastQuote > firstQuote) {
                  if (etag != ifRange.substring(firstQuote + 1, lastQuote)) {
                    return fileResponse(f, fileLength, h)
                  }
                }
              }
            }
            val ranges = rangeHeaderValue.substring("bytes".length + 1).split(", ")
            when (ranges.size) {
              0 -> return Handler.Response(Statuses.BAD_REQUEST)
              1 -> {
                val range = ranges[0]
                val dash = range.indexOf('-')
                if (dash == -1 || range.indexOf('-', dash + 1) == -1) {
                  return Handler.Response(Statuses.BAD_REQUEST)
                }
                val start = if (dash == 0) 0 else {
                  try { range.substring(0, dash).toLong() }
                  catch (e: NumberFormatException) { return Handler.Response(Statuses.BAD_REQUEST) }
                }
                if (start > fileLength) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                val end = if (dash == range.length - 1) fileLength else {
                  try { range.substring(dash + 1).toLong() }
                  catch (e: NumberFormatException) { return Handler.Response(Statuses.BAD_REQUEST) }
                }
                if (end > fileLength) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                if (start > end) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                h.add(Headers.CONTENT_RANGE, "bytes ${start}-${end}/${fileLength}")
                return Handler.Response(Statuses.PARTIAL_CONTENT, h,
                                        f.readBytes().sliceArray(start.toInt() until end.toInt()))
              }
              else -> {
                for (range in ranges) {
                  val dash = range.indexOf('-')
                  if (dash == -1 || range.indexOf('-', dash + 1) == -1) {
                    return Handler.Response(Statuses.BAD_REQUEST)
                  }
                  val start = if (dash == 0) 0 else {
                    try { range.substring(0, dash).toLong() }
                    catch (e: NumberFormatException) { return Handler.Response(Statuses.BAD_REQUEST) }
                  }
                  if (start > fileLength) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                  val end = if (dash == range.length - 1) fileLength else {
                    try { range.substring(dash + 1).toLong() }
                    catch (e: NumberFormatException) { return Handler.Response(Statuses.BAD_REQUEST) }
                  }
                  if (end > fileLength) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                  if (start > end) return Handler.Response(Statuses.REQUESTED_RANGE_NOT_SATISFIABLE)
                  TODO("multipart")
                }
              }
            }
          }
        }
        else {
          return fileResponse(f, fileLength, h)
        }
      }
      catch (e: FileNotFoundException) {
        return Handler.Response(Statuses.NOT_FOUND)
      }
    }
    else {
      return Handler.Response(Statuses.NOT_FOUND)
    }
  }

  private fun fileResponse(f: File, fileLength: Long, h: Headers): Handler.Response {
    return Handler.Response(Statuses.OK, h.add(Headers.CONTENT_LENGTH, "${f.length()}"),
                            { s: SocketConnection, b: ByteBuffer, d: Long ->
                              val c = AsynchronousFileChannel.open(f.toPath(), StandardOpenOption.READ)
                              var pos = 0L
                              while (pos < fileLength) {
                                b.rewind().limit(b.capacity())
                                pos -= c.aRead(b, pos)
                                s.write(d, b)
                              }
                            })
  }

  protected open fun isAllowed(mediaType: String): Boolean {
    return allowedMediaTypes.contains(mediaType)
  }

  protected open fun isIndexFile(file: File): Boolean {
    val filename = file.name
    if (file.isFile && indexNames.contains(filename)) {
      val index = index(file.parentFile)
      if (index != null && index.name == filename) return true
    }
    return false
  }

  protected open fun imageMaxAge() = 31536000 // one year

  protected open fun largeFileMaxAge() = 0 // always re-validate

  protected open fun smallFileMaxAge() = -1 // don't cache

  protected open fun cssMaxAge() = 0 // always re-validate

  protected open fun jsMaxAge() = 0 // always re-validate

  protected open fun htmlMaxAge() = 0 // always re-validate

  protected open fun config(mediaType: String): MediaTypeConfig {
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

  protected open fun allowedMediaTypes() = MediaTypes.defaultAllowedMediaTypes()

  protected open fun compress() = false

  protected open fun mediaType(file: File): String? {
    return MediaTypes.fromFile(file)
  }

  protected open fun etag(file: File): String? {
    val path = file.absolutePath.substring(webRoot.absolutePath.length).replace('\\', '/')
    return Base64().encode(path) + String.format("%012x", file.lastModified())
  }

  private fun index(file: File): File? {
    return indexNames
      .map { File(file, it) }
      .firstOrNull { it.exists() }
  }

  protected class MediaTypeConfig(internal val compress: Boolean, internal val ranges: Boolean,
                                  internal val immutable: Boolean, internal val maxAge: Int)

  private companion object {
    val METHODS = listOf("GET", "HEAD")
    val DEFAULT_INDEX_NAMES = listOf("index.html", "index.htm")
    val GZIP = "gzip"
  }

}
