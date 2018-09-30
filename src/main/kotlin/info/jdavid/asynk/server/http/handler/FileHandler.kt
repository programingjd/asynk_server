package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Status
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.CacheControl
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.FileRoute
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.Base64

/**
 * File-based HTTP Handler.<br>
 * By default, it uses the default [MediaType.CacheControl] policies.<br>
 * ETags are generated based on the last modification date and time.<br>
 * No cache is used by default. All new requests (unless Unmodified is returned because of the ETag) are
 * reading the file content directly from disk. It is possible (and recommended) to extend this handler
 * to implement a caching mechanism.
 */
open class FileHandler(route: FileRoute): HttpHandler<HttpHandler.Acceptance<File>,
                                                      File,
                                                      AbstractHttpHandler.Context,
                                                      File>(route) {

  companion object {
    internal fun serveDirectory(directory: File, port: Int) {
      Server(
        FileHandler(FileRoute(directory)),
        InetSocketAddress(InetAddress.getLoopbackAddress(), port),
        4096
      ).use {
        Thread.sleep(Long.MAX_VALUE)
      }
    }

    /**
     * Starts a server that serves the current directory on localhost:8080.
     * @param args are not used.
     */
    @JvmStatic fun main(args: Array<String>) {
      serveDirectory(File("."), 8080)
    }
  }

  final override suspend fun handle(acceptance: Acceptance<File>, headers: Headers, body: ByteBuffer,
                                    context: Context): Response<*> {
    val uriParams = acceptance.routeParams
    val file = if (uriParams.isDirectory) {
      val path = Uri.path(acceptance.uri)
      if (path.last() != '/') return redirect("${path}/${acceptance.uri.substring(path.length)}")
      indexFilenames().map { File(uriParams, it) }.firstOrNull { it.exists() } ?: return forbidden(context)
    } else uriParams
    val mediaType = mediaType(file) ?: return forbidden(context)
    val cacheControl = mediaTypes()[mediaType] ?: return forbidden(context)
    val responseHeaders = Headers()
    responseHeaders.set(Headers.CACHE_CONTROL, cacheControl.value())
    if (cacheControl.maxAge > -1) {
      val etag = etag(file) ?: return FileResponse(file, mediaType, responseHeaders)
      responseHeaders.set(Headers.ETAG, etag)
      if (etag == headers.value(Headers.IF_NONE_MATCH)) return notModified(responseHeaders)
      return FileResponse(file, mediaType, responseHeaders)
    }
    else {
      return FileResponse(file, mediaType, responseHeaders)
    }
  }

  /**
   * Returns the ETag value for the specified file.<br>
   * By default, the ETag is generated based on the file last modification date and time.
   * @param file the file to serve.
   * @return the ETag value (can be null, in which case no ETag will be sent).
   */
  protected open fun etag(file: File): String? {
    return Base64.getUrlEncoder().encodeToString(
      String.format("%012x", file.lastModified()).toByteArray(Charsets.US_ASCII)
    )
  }

  /**
   * Returns the media type of the file or null if it is not known.
   * @param file the file.
   * @return the file media type or null if it is not known.
   */
  protected open fun mediaType(file: File): String? {
    return MediaType.fromFile(file)
  }


    /**
   * Returns the media types that are allowed with their cache control policies.
   * @return the map of key/value entries with the key being the allowed media type and the value being the
   * associated cache control policy.
   */
  protected open fun mediaTypes() = CacheControl.defaultCacheControls

  /**
   * Returns an (ordered) sequence of file names that can be used as directory "index" pages. The first match
   * is used. By defaults, this only includes "index.html".
   * @return the sequence of file names for index pages.
   */
  protected open fun indexFilenames(): Sequence<String> = sequenceOf("index.html")

  private fun notModified(h: Headers) = object: Response<Nothing>(
    Status.NOT_MODIFIED, null, h) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }

  private fun redirect(uri: String) = object: Response<Nothing>(
    Status.MOVED_PERMANENTLY) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }.header(Headers.LOCATION, uri)

  /**
   * Creates a 403 Forbidden response. By default, this sends a response with no body.
   */
  private fun forbidden(context: Context) = object: Response<Nothing>(
    Status.FORBIDDEN) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
    override suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer, method: Method) {
      context.FORBIDDEN.flip()
      socket.asyncWrite(context.FORBIDDEN, true)
    }
  }.header(Headers.CONNECTION, "close")

  override suspend fun context(others: Collection<*>?) = Context(others)

  override suspend fun acceptUri(method: Method, uri: String, params: File): Acceptance<File>? {
    return Acceptance(false, false, method, uri, params)
  }

}
