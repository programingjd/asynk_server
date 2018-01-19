package info.jdavid.server.http

import info.jdavid.server.Server
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.nio.ByteBuffer

class DigestAuthTests {

  class HttpTestHandler: HttpHandler<HttpHandler.Acceptance, AbstractHttpHandler.Context>(null) {

    override suspend fun handle(acceptance: Acceptance,
                                headers: Headers,
                                body: ByteBuffer,
                                context: Context): Response<*> {
      return object: Response<ByteArray>(Status.OK) {
        override fun bodyMediaType() = MediaType.TEXT
        override suspend fun bodyByteLength() = this.body?.size?.toLong() ?: 0L
        override suspend fun writeBody(buffer: ByteBuffer) {
          buffer.put(this.body)
        }
      }.body("Test".toByteArray(Charsets.US_ASCII))
    }

    override fun context() = Context()

    override suspend fun acceptUri(method: Method, uri: String): HttpHandler.Acceptance {
      return HttpHandler.Acceptance(true, false, method, uri, null)
    }

  }

  class AuthContext(c: AbstractHttpHandler.Context): AuthHandler.Context<AbstractHttpHandler.Context>(c) {
    val users = mapOf("user1" to "password1", "user2" to "password2")
  }

  class DigestAuthTestHandler: DigestAuthHandler<HttpHandler.Acceptance,
                                                 AbstractHttpHandler.Context,
                                                 AuthContext>(HttpTestHandler(),
                                                              "Test Realm",
                                                              seed) {

    override fun ha1(username: String, context: AuthContext): String? {
      return context.users[username]?.let { ha1(username, it) }
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
      DigestAuthTestHandler(),
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

  companion object {
    val seed = "Test123abc!@#L<e".toByteArray(Charsets.US_ASCII)
  }

}
