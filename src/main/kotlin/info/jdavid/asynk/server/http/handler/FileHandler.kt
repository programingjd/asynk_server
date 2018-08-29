package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.Status
import info.jdavid.asynk.server.http.Uri
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.FileRoute
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.Base64

open class FileHandler(route: FileRoute): HttpHandler<HttpHandler.Acceptance<File>,
                                                      AbstractHttpHandler.Context,
                                                      File>(route) {

  companion object {
    internal fun serveDirectory(directory: File, port: Int) {
      Server(
        HttpHandlerChain(
          listOf(FileHandler(FileRoute(directory)))
        ),
        InetSocketAddress(InetAddress.getLoopbackAddress(), port),
        4096
      ).use {
        Thread.sleep(Long.MAX_VALUE)
      }
    }
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
    val mediaType = MediaType.fromFile(file) ?: return forbidden(context)
    val cacheControl = mediaTypes()[mediaType] ?: return forbidden(context)
    val responseHeaders = Headers()
    responseHeaders.set(Headers.CACHE_CONTROL, cacheControl.value())
    if (cacheControl.maxAge > -1) {
      val etag = etag(file) ?: return FileResponse(file, mediaType, responseHeaders)
      responseHeaders.set(Headers.ETAG, etag)
      if (etag == headers.value(
          Headers.IF_NONE_MATCH)) return notModified(responseHeaders)
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

  protected fun forbidden(context: Context) = object: Response<Nothing>(
    Status.FORBIDDEN) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
    override suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer, method: Method) {
      socket.aWrite(context.FORBIDDEN.flip() as ByteBuffer)
    }
  }.header(Headers.CONNECTION, "close")

  protected fun notFound(context: Context) = object: Response<Nothing>(
    Status.NOT_FOUND) {
    override fun bodyMediaType(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun bodyByteLength(body: Nothing) = throw UnsupportedOperationException()
    override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {}
    override suspend fun write(socket: AsynchronousSocketChannel, buffer: ByteBuffer, method: Method) {
      socket.aWrite(context.NOT_FOUND.flip() as ByteBuffer)
    }
  }.header(Headers.CONNECTION, "close")

  override suspend fun context(others: Collection<*>?) = Context(others)

  override suspend fun acceptUri(method: Method, uri: String, params: File): Acceptance<File>? {
    return Acceptance(false, false, method, uri, params)
  }

}
