package info.jdavid.server.http

import kotlinx.coroutines.experimental.nio.aWrite
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.Base64

open class FileHandler(route: FileRoute): HttpHandler<HttpHandler.Acceptance<File>,
                                                      AbstractHttpHandler.Context,
                                                      File>(route) {

  final override suspend fun handle(acceptance: Acceptance<File>, headers: Headers, body: ByteBuffer,
                                   context: Context): Response<*> {
    val uriParams = acceptance.routeParams ?: return notFound(context)
    val file = if (uriParams.isDirectory) {
      val path = Uri.path(acceptance.uri)
      if (path.last() != '/') return redirect("${path}/${acceptance.uri.substring(path.length)}")
      indexFilenames().map { File(uriParams, it) }.firstOrNull { it.exists() } ?: return forbidden(context)
    } else uriParams
    val mediaType = MediaType.fromFile(file) ?: return forbidden(context)
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

  protected open fun etag(file: File): String? {
    return Base64.getUrlEncoder().encodeToString(
      String.format("%012x", file.lastModified()).toByteArray(Charsets.US_ASCII)
    )
  }

  protected open fun mediaTypes() = MediaType.defaultCacheControls

  protected open fun indexFilenames(): Sequence<String> = sequenceOf("index.html")

  private fun notModified(h: Headers) = object: Response<Nothing>(Status.NOT_MODIFIED, null, h) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }

  private fun redirect(uri: String) = object: Response<Nothing>(Status.MOVED_PERMANENTLY) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
  }.header(Headers.LOCATION, uri)

  protected fun forbidden(context: Context) = object: Response<Nothing>(Status.FORBIDDEN) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
    override suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      socket.aWrite(context.FORBIDDEN.flip() as ByteBuffer)
    }
  }.header(Headers.CONNECTION, "close")

  protected fun notFound(context: Context) = object: Response<Nothing>(Status.NOT_FOUND) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
    override suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
      socket.aWrite(context.NOT_FOUND.flip() as ByteBuffer)
    }
  }.header(Headers.CONNECTION, "close")

  final override fun context() = AbstractHttpHandler.Context()

  final override suspend fun acceptUri(method: Method, uri: String): Acceptance<File>? {
    val file = route?.match(method, uri) ?: return null
    return Acceptance(false, false, method, uri, file)
  }

}
