package info.jdavid.server.http.handler

import info.jdavid.server.Server
import info.jdavid.server.http.Headers
import info.jdavid.server.http.MediaType
import info.jdavid.server.http.Method
import info.jdavid.server.http.Status
import info.jdavid.server.http.base.AbstractHttpHandler
import info.jdavid.server.http.base.AuthHandler
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

class BasicAuthTests {

  class HttpTestHandler: HttpHandler<HttpHandler.Acceptance<Nothing>,
    AbstractHttpHandler.Context,
    Nothing>(null) {

    override suspend fun handle(acceptance: Acceptance<Nothing>,
                                headers: Headers,
                                body: ByteBuffer,
                                context: Context): Response<*> {
      return object: Response<ByteArray>(
        Status.OK) {
        override fun bodyMediaType(body: ByteArray) = MediaType.TEXT
        override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
        override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
          this.body?.let { buffer.clear().put(it) }
        }
      }.body("Test".toByteArray(Charsets.US_ASCII))
    }

    override fun context() = Context()

    override suspend fun acceptUri(method: Method, uri: String): Acceptance<Nothing> {
      return Acceptance(true, false, method, uri, null)
    }

  }

  class AuthContext(c: AbstractHttpHandler.Context): AuthHandler.Context<AbstractHttpHandler.Context>(c) {
    val users = mapOf("user1" to "password1", "user2" to "password2")
  }

  class BasicAuthTestHandler: BasicAuthHandler<HttpHandler.Acceptance<Nothing>,
    AbstractHttpHandler.Context,
    AuthContext,
    Nothing>(HttpTestHandler(), "Test Realm") {
    override fun credentialsAreValid(auth: String, context: AuthContext): Boolean {
      return context.users.toList().find { authorizationHeaderValue(
        it.first, it.second) == auth } != null
    }

    override fun context() = AuthContext(delegate.context())

  }

  private fun context(user: String? = null, password: String? = null): HttpClientContext {
    val credentials = BasicCredentialsProvider()
    if (user != null && password != null) {
      credentials.setCredentials(AuthScope.ANY, UsernamePasswordCredentials (user, password))
    }
    return HttpClientContext.create().apply {
      credentialsProvider = credentials
      authCache = BasicAuthCache()
    }
  }

  @Test fun test() {
    Server(
      BasicAuthTestHandler(),
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8080),
      4096
    ).use {
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
      }
      HttpClientBuilder.create().build().use {
        it.execute(request, context()).use {
          assertEquals(401, it.statusLine.statusCode)
        }
        it.execute(request, context("user1", "")).use {
          assertEquals(401, it.statusLine.statusCode)
        }
        it.execute(request, context("user", "password1")).use {
          assertEquals(401, it.statusLine.statusCode)
        }
        it.execute(request, context("user1", "password1")).use {
          assertEquals(200, it.statusLine.statusCode)
        }
      }
    }
  }

}
