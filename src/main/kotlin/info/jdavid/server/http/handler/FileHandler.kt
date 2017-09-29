package info.jdavid.server.http.handler

import info.jdavid.server.Base64
import info.jdavid.server.SocketConnection
import info.jdavid.server.http.Encodings
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
import java.util.*

open class FileHandler(regex: String,
                       protected val webRoot: File,
                       indexNames: List<String> = DEFAULT_INDEX_NAMES): RegexHandler(METHODS, regex) {
  constructor(webRoot: File, indexNames: List<String>): this("(.*)", webRoot, indexNames)
  constructor(webRoot: File): this("(.*)", webRoot)

  private val indexNames = indexNames.asSequence()
  private val allowedMediaTypes = mutableSetOf<String>()

  override fun setup(): FileHandler {
    super.setup()
    allowedMediaTypes.addAll(allowedMediaTypes())
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
      if (pathLength > 0 && path[pathLength - 1] != SLASH) {
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
        val compression = if (config.compress) {
          selectCompression(f, headers.value(Headers.ACCEPT_ENCODING))
        } else null
        val h = Headers()
        h.add(Headers.CACHE_CONTROL, config.cacheControl)
        if (config.maxAge != -1) if (etag != null) h.add(Headers.ETAG, etag)
        val fileLength = f.length()
        if (config.ranges) {
          h.add(Headers.ACCEPT_RANGES, BYTES)
          val rangeHeaderValue = headers.value(Headers.RANGE)
          if (rangeHeaderValue == null) {
            return fullResponse(f, fileLength, m, h, compression, method != HEAD)
          }
          else {
            if (!rangeHeaderValue.startsWith(BYTES)) return Handler.Response(Statuses.BAD_REQUEST)
            if (etag != null) {
              val ifRange = headers.value(Headers.IF_RANGE)
              if (ifRange != null) {
                val firstQuote = ifRange.indexOf(QUOTE)
                val lastQuote = ifRange.lastIndexOf(QUOTE)
                if (firstQuote != -1 && lastQuote > firstQuote) {
                  if (etag != ifRange.substring(firstQuote + 1, lastQuote)) {
                    return fullResponse(f, fileLength, m, h, compression, method != HEAD)
                  }
                }
              }
            }
            val ranges = rangeHeaderValue.substring(BYTES.length + 1).split(COMMA)
            when (ranges.size) {
              0 -> return Handler.Response(Statuses.BAD_REQUEST)
              1 -> {
                val range = ranges[0].trim()
                val dash = range.indexOf(DASH)
                if (dash == -1 || range.indexOf(DASH, dash + 1) != -1) {
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
                return partialResponse(f, fileLength, m, h, start, end, compression, method != HEAD)
              }
              else -> {
                val multipart = ArrayList<LongRange>(ranges.size)
                for (it in ranges) {
                  val range = it.trim()
                  val dash = range.indexOf(DASH)
                  if (dash == -1 || range.indexOf(DASH, dash + 1) != -1) {
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
                  multipart.add(start..end)
                }
                return rangesResponse(f, fileLength, m, h, multipart, compression, method != HEAD)
              }
            }
          }
        }
        else {
          return fullResponse(f, fileLength, m, h, compression, method != HEAD)
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

  protected open fun preCompressedFile(file: File, compression: String): File? {
    return when (compression) {
      BROTLI -> {
        val compressed = File(file.parent, "${file.name}.br")
        if (compressed.isFile) compressed else null
      }
      GZIP -> {
        val compressed = File(file.parent, "${file.name}.gz")
        if (compressed.isFile) compressed else null
      }
      else -> null
    }
  }

  protected open fun selectCompression(file: File, accepted: String?): String? {
    if (accepted == null) return null
    if (accepted.contains(BROTLI) && preCompressedFile(file, BROTLI) != null) return BROTLI
    if (accepted.contains(GZIP) && preCompressedFile(file, GZIP) != null) return GZIP
    return null
  }

  private suspend fun fileContent(file: File, start: Long, end: Long,
                                  socketConnection: SocketConnection, buffer: ByteBuffer,
                                  deadline: Long) {
    val channel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
    var position = start
    val capacity = buffer.capacity()
    while (position < end) {
      buffer.rewind().limit(capacity)
      if (capacity > end - position) {
        buffer.limit((end - position).toInt())
      }
      else {
        buffer.limit(capacity)
      }
      position += channel.aRead(buffer, position)
      socketConnection.write(deadline, buffer)
    }
  }

  protected open fun fullResponse(file: File, fileLength: Long, mediaType: String, headers: Headers,
                                  compression: String?,
                                  includeBody: Boolean): Handler.Response {
    headers.add(Headers.CONTENT_TYPE, mediaType)
    if (compression == null) {
      headers.add(Headers.CONTENT_LENGTH, "${fileLength}")
      return if (includeBody) {
        Handler.Response(Statuses.OK, headers,
                         { s: SocketConnection, b: ByteBuffer, d: Long ->
                           fileContent(file, 0, fileLength, s, b, d)
                         })
      }
      else {
        Handler.Response(Statuses.OK, headers)
      }
    }
    else {
      val compressedFile = preCompressedFile(file, compression) ?: throw NullPointerException()
      val compressedFileLength = compressedFile.length()
      headers.add(Headers.CONTENT_ENCODING, compression)
      headers.add(Headers.CONTENT_LENGTH, "${compressedFileLength}")

      return if (includeBody) {
        Handler.Response(Statuses.OK, headers,
                         { s: SocketConnection, b: ByteBuffer, d: Long ->
                           fileContent(compressedFile, 0, compressedFileLength, s, b, d)
                         })
      } else {
        Handler.Response(Statuses.OK, headers)
      }
    }
  }

  protected open fun partialResponse(file: File, fileLength: Long, mediaType: String, headers: Headers,
                                     start: Long, end: Long, compression: String?,
                                     includeBody: Boolean): Handler.Response {
    // compression is not supported by default because we need to know the Content-Type ahead of time.
    headers.add(Headers.CONTENT_TYPE, mediaType)
    headers.add(Headers.CONTENT_RANGE, "${BYTES} ${start}-${end}/${fileLength}")
    headers.add(Headers.CONTENT_LENGTH, "${end-start}")
    return if (includeBody) {
      Handler.Response(Statuses.PARTIAL_CONTENT, headers,
                       { s: SocketConnection, b: ByteBuffer, d: Long ->
                         fileContent(file, start, end, s, b, d)
                       })
    }
    else {
      Handler.Response(Statuses.PARTIAL_CONTENT, headers)
    }
  }

  protected open fun rangesResponse(file: File, fileLength: Long, mediaType: String, headers: Headers,
                                    ranges: List<LongRange>, compression: String?,
                                    includeBody: Boolean): Handler.Response {
    // compression is not supported by default because we need to know the Content-Type ahead of time.
    val m = mediaType.toByteArray(Encodings.ASCII)
    val n = "${fileLength}".toByteArray(Encodings.ASCII)
    headers.add(Headers.CONTENT_TYPE, BYTE_RANGES_MULTIPART_CONTENT_TYPE)
    var contentLength = (BYTE_RANGES_MULTIPART_0.size + n.size).toLong()
    for (range in ranges) {
      contentLength += BYTE_RANGES_MULTIPART_1.size
      contentLength += m.size
      contentLength += BYTE_RANGES_MULTIPART_2.size
      contentLength += stringSize(range.first) + 1 + stringSize(range.last) + 1 + n.size
      contentLength += BYTE_RANGES_MULTIPART_3.size
      contentLength += range.last - range.first
    }
    headers.add(Headers.CONTENT_LENGTH, "${contentLength}")
    return if (includeBody) {
      Handler.Response(Statuses.PARTIAL_CONTENT, headers,
                       { s: SocketConnection, b: ByteBuffer, d: Long ->
                         b.rewind().limit(b.capacity())
                         b.put(BYTE_RANGES_MULTIPART_0)
                         for (range in ranges) {
                           b.put(BYTE_RANGES_MULTIPART_1)
                           b.put(m)
                           b.put(BYTE_RANGES_MULTIPART_2)
                           b.put("${range.first}-${range.last}/".toByteArray(Encodings.ASCII))
                           b.put(n)
                           b.put(BYTE_RANGES_MULTIPART_3)
                           s.write(d, b)
                           fileContent(file, range.first, range.last, s, b, d)
                         }
                       })
    }
    else {
      Handler.Response(Statuses.PARTIAL_CONTENT, headers)
    }
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
                                  immutable: Boolean, internal val maxAge: Int) {
    internal val cacheControl = when (maxAge) {
      -1 -> NO_STORE
      0  -> NO_CACHE
      else -> "max-age=" + maxAge + (if (immutable) ", immutable" else ", must-revalidate")
    }
  }

  private companion object {
    val GET = "GET"
    val HEAD = "HEAD"
    val METHODS = listOf(GET, HEAD)
    val DEFAULT_INDEX_NAMES = listOf("index.html", "index.htm")
    val NO_STORE = "no-store"
    val NO_CACHE = "no-cache"
    val GZIP = "gzip"
    val BROTLI = "br"
    val BYTES = "bytes"
    val SLASH='/'
    val QUOTE = '"'
    val DASH = '-'
    val COMMA = ','
    val BYTE_RANGES_MULTIPART_CONTENT_TYPE = "multipart/byteranges; boundary=__Multipart_Boundary__"
    val BYTE_RANGES_MULTIPART_0 = "\r\n--__Multipart_Boundary__--\r\n".toByteArray(Encodings.ASCII)
    val BYTE_RANGES_MULTIPART_1 =
      "\r\n--__Multipart_Boundary__--\r\n${Headers.CONTENT_TYPE}: ".toByteArray(Encodings.ASCII)
    val BYTE_RANGES_MULTIPART_2 = "\r\n${Headers.CONTENT_RANGE}: ${BYTES} ".toByteArray(Encodings.ASCII)
    val BYTE_RANGES_MULTIPART_3 = "\r\n\r\n".toByteArray(Encodings.ASCII)
    private val sizes = longArrayOf(
      9,
      99,
      999, // 1kb
      9999,
      99999,
      999999, // 1mb
      9999999,
      99999999,
      999999999, // 1gb
      9999999999,
      99999999999,
      999999999999, // 1tb
      9999999999999,
      99999999999999
    )
    fun stringSize(n: Long): Int {
      var i = 0
      while (true) {
        if (n < sizes[i++]) return i
      }
    }
  }

}
