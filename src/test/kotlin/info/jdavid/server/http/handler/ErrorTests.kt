package info.jdavid.server.http.handler

import info.jdavid.server.Server
import info.jdavid.server.http.Headers
import info.jdavid.server.http.MediaType
import info.jdavid.server.http.Method
import info.jdavid.server.http.Status
import info.jdavid.server.http.base.AbstractHttpHandler
import info.jdavid.server.http.base.AuthHandler
import info.jdavid.server.http.route.NoParams
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class ErrorTests {

  class FailingHandler: HttpHandler<HttpHandler.Acceptance<NoParams>,
    AbstractHttpHandler.Context,
    NoParams>(NoParams) {

    override suspend fun handle(acceptance: Acceptance<NoParams>,
                                headers: Headers,
                                body: ByteBuffer,
                                context: Context): Response<*> {
      throw Exception()
    }

    override suspend fun context(others: Collection<*>?) = Context(others)

    override suspend fun acceptUri(method: Method, uri: String, params: NoParams): Acceptance<NoParams> {
      return Acceptance(true, false, method, uri, params)
    }

  }

  @Test fun test() {
    Server(
      FailingHandler()
    ).use {
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
      }
      HttpClientBuilder.create().build().use {
        it.execute(request).use {
          assertEquals(500, it.statusLine.statusCode)
        }
      }
    }
  }

}
