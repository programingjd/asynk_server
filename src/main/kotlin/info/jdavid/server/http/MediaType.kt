
package info.jdavid.server.http

import java.io.File

class MediaType private constructor() {

  @Suppress("MemberVisibilityCanPrivate", "unused")
  companion object {
    val CSS = "text/css"
    val CSV = "text/csv"
    val HTML = "text/html"
    val TEXT = "text/plain"
    val URI_LIST = "text/uri-list"
    val XML = "text/xml"

    val ATOM = "application/atom+xml"
    val GZIP = "application/gzip"
    val TAR = "application/x-gtar"
    var XZ = "application/z-xz"
    val SEVENZ = "application/x-7z-compressed"
    val ZIP = "application/zip"
    val RAR = "application/vnd.rar"
    val JAR = "application/java-archive"
    val APK = "application/vnd.android.package-archive"
    val DMG = "application/x-apple-diskimage"
    val ISO = "application/x-iso9660-image"
    val JAVASCRIPT = "application/javascript"
    val JSON = "application/json"
    val OCTET_STREAM = "application/octet-stream"
    val PDF = "application/pdf"
    val WOFF = "application/font-woff"
    val WOFF2 = "font/woff2"
    val XHTML = "application/xhtml+xml"
    val WEB_MANIFEST = "application/manifest+json"

    val OTF = "application/opentype"
    val TTF = "application/truetype"
    val EOT = "application/vnd.ms-fontobject"

    val WAV = "audio/x-wav"
    val MP3 = "audio/mpeg"
    val OGG = "audio/ogg"

    val MP4 = "video/mp4"
    val OGV = "video/ogg"
    val WEBM = "video/webm"

    val EMAIL = "message/rfc822"

    val BYTE_RANGES = "multipart/byteranges"
    val DIGEST = "multipart/digest"
    val FORM_DATA = "multipart/form-data"
    val MIXED = "multipart/mixed"
    val SIGNED = "multipart/signed"
    val ENCRYPTED = "multipart/encrypted"

    val JPG = "image/jpeg"
    val PNG = "image/png"
    val GIF = "image/gif"
    val BMP = "image/bmp"
    val SVG = "image/svg+xml"
    val ICO = "image/x-icon"
    val WEBP = "image/webp"

    val SSE = "text/event-stream"

    val DIRECTORY = "text/directory"

    private val map = mapOf(
      "html" to HTML,
      "css" to CSS,
      "js" to JAVASCRIPT,
      "jpg" to JPG,
      "png" to PNG,
      "htm" to HTML,
      "xhtml" to XHTML,
      "woff" to WOFF,
      "woff2" to WOFF2,
      "webmanifest" to WEB_MANIFEST,
      "otf" to OTF,
      "ttf" to TTF,
      "eot" to EOT,
      "gif" to GIF,
      "bmp" to BMP,
      "svg" to SVG,
      "ico" to ICO,
      "webp" to WEBP,

      "csv" to CSV,
      "txt" to TEXT,
      "howto" to TEXT,
      "readme" to TEXT,
      "xml" to XML,
      "xsl" to XML,
      "gz" to GZIP,
      "tar" to TAR,
      "xz" to XZ,
      "7z" to SEVENZ,
      "zip" to ZIP,
      "rar" to RAR,
      "jar" to JAR,
      "apk" to APK,
      "dmg" to DMG,
      "iso" to ISO,
      "json" to JSON,
      "bin" to OCTET_STREAM,
      "pdf" to PDF,

      "wav" to WAV,
      "mp3" to MP3,
      "ogg" to OGG,
      "mp4" to MP4,
      "ogv" to OGV,
      "webm" to WEBM
    )

    fun fromFile(file: File): String? {
      if (file.isDirectory) return DIRECTORY
      val filename = file.name
      if ("." == filename || ".." == filename) return DIRECTORY
      val i = filename.lastIndexOf('.')
      if (i == -1) {
        if (!file.isFile) return DIRECTORY
        val parent = file.parentFile
        return if (parent != null && "acme-challenge" == parent.name) TEXT else null
      }
      val ext = filename.substring(i+1).toLowerCase()
      return map[ext]
    }

    fun defaultAllowedMediaTypes() = setOf(
      HTML, XHTML, XML, JSON, ATOM, WEB_MANIFEST,
      CSS, JAVASCRIPT, TEXT, CSV,
      PNG, JPG, GIF, ICO, WEBP, SVG,
      WOFF, WOFF2, TTF, OTF, EOT,
      PDF, OCTET_STREAM, ZIP, SEVENZ, TAR, XZ,
      MP4, OGV, WEBM, MP3, OGG
    )

  }

}
