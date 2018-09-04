package info.jdavid.asynk.server.http

import info.jdavid.asynk.http.MediaType
import java.util.Collections

/**
 * Cache-Control policy information used to store desired cache control options for a particular media type.
 * @param immutable the immutable Cache-Control flag.
 * @param maxAge the Cache-Control maxAge value (-1 represents no-store, 0 represents no-cache).
 */
class CacheControl(val immutable: Boolean, val maxAge: Int) {
  fun value() = when (maxAge) {
    -1 -> "no-store"
    0 -> "no-cache"
    else -> "max-age=${maxAge} ${if (immutable) "immutable" else "must-revalidate"}"
  }

  companion object {
    private const val imageMaxAge = 31536000     // one year
    private const val largeFileMaxAge = 0        // always re-validate
    private const val smallFileMaxAge = -1       // don't cache
    private const val cssMaxAge = 0              // always re-validate
    private const val fontMaxAge = 31536000      // one year
    private const val jsMaxAge = 0               // always re-validate
    private const val htmlMaxAge = 0             // always re-validate

    /**
     * The default Cache-Control policies for the various media types.
     */
    val defaultCacheControls: Map<String, CacheControl> =
      Collections.unmodifiableMap(
        mapOf(
          MediaType.HTML to CacheControl(false, htmlMaxAge),
          MediaType.XHTML to CacheControl(false, htmlMaxAge),
          MediaType.XML to CacheControl(false, smallFileMaxAge),
          MediaType.JSON to CacheControl(false, smallFileMaxAge),
          MediaType.ATOM to CacheControl(false, largeFileMaxAge),
          MediaType.WEB_MANIFEST to CacheControl(false, htmlMaxAge),
          MediaType.CSS to CacheControl(false, cssMaxAge),
          MediaType.JAVASCRIPT to CacheControl(false, jsMaxAge),
          MediaType.TEXT to CacheControl(false, htmlMaxAge),
          MediaType.CSV to CacheControl(false, largeFileMaxAge),
          MediaType.PNG to CacheControl(true, imageMaxAge),
          MediaType.JPG to CacheControl(true, imageMaxAge),
          MediaType.GIF to CacheControl(true, imageMaxAge),
          MediaType.ICO to CacheControl(true, imageMaxAge),
          MediaType.WEBP to CacheControl(true, imageMaxAge),
          MediaType.SVG to CacheControl(true, imageMaxAge),
          MediaType.WOFF to CacheControl(true, fontMaxAge),
          MediaType.WOFF2 to CacheControl(true, fontMaxAge),
          MediaType.TTF to CacheControl(true, fontMaxAge),
          MediaType.OTF to CacheControl(true, fontMaxAge),
          MediaType.EOT to CacheControl(true, fontMaxAge),
          MediaType.PDF to CacheControl(false, largeFileMaxAge),
          MediaType.OCTET_STREAM to CacheControl(false, largeFileMaxAge),
          MediaType.ZIP to CacheControl(false, largeFileMaxAge),
          MediaType.SEVENZ to CacheControl(false, largeFileMaxAge),
          MediaType.TAR to CacheControl(false, largeFileMaxAge),
          MediaType.XZ to CacheControl(false, largeFileMaxAge),
          MediaType.MP4 to CacheControl(false, largeFileMaxAge),
          MediaType.OGV to CacheControl(false, largeFileMaxAge),
          MediaType.WEBM to CacheControl(false, largeFileMaxAge),
          MediaType.MP3 to CacheControl(false, largeFileMaxAge),
          MediaType.OGG to CacheControl(false, largeFileMaxAge)
        )
      )
  }

}
