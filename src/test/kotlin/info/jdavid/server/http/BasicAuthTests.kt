package info.jdavid.server.http

import info.jdavid.server.Server
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.nio.ByteBuffer

class BasicAuthTests {

  class HttpTestHandler: HttpHandler<HttpHandler.Acceptance, AbstractHttpHandler.Context>() {

    suspend override fun handle(acceptance: Acceptance,
                                headers: Headers,
                                body: ByteBuffer,
                                context: Context): Response<*> {
      return object: Response<ByteArray>(Status.OK) {
        override fun bodyMediaType() = MediaType.TEXT
        suspend override fun bodyByteLength() = this.body?.size?.toLong() ?: 0L
        suspend override fun writeBody(buffer: ByteBuffer) {
          buffer.put(this.body)
        }
      }.body("Test".toByteArray(Charsets.US_ASCII))
    }

    override fun context() = Context()

    suspend override fun acceptUri(method: Method, uri: String): HttpHandler.Acceptance {
      return HttpHandler.Acceptance(true, false, method, uri)
    }

  }

  class AuthContext(c: AbstractHttpHandler.Context): AuthHandler.Context<AbstractHttpHandler.Context>(c) {
    val users = mapOf("user1" to "password1", "user2" to "password2")
  }

  class BasicAuthTestHandler: BasicAuthHandler<HttpHandler.Acceptance,
                                               AbstractHttpHandler.Context,
                                               AuthContext>(HttpTestHandler(), "Test Realm") {
    override fun credentialsAreValid(auth: String, context: AuthContext): Boolean {
      return context.users.toList().find { authorizationHeaderValue(it.first, it.second) == auth } != null
    }

    override fun context() = AuthContext(delegate.context())

  }

  private fun context(user: String? = null, password: String? = null): HttpClientContext {
    val host = HttpHost("localhost", 8080, "http")
    val cache = BasicAuthCache().apply {
      put(host, BasicScheme())
    }
    val credentials = BasicCredentialsProvider()
    if (user != null && password != null) {
      credentials.setCredentials(AuthScope.ANY, UsernamePasswordCredentials (user, password))
    }
    return HttpClientContext.create().apply {
      credentialsProvider = credentials
      authCache = cache
    }
  }

  @Test
  fun test() {
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
