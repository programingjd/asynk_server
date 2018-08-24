package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.Status
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import info.jdavid.asynk.server.http.route.NoParams
import kotlinx.coroutines.experimental.nio.aWrite
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

class BasicAuthTests {

  class HttpTestHandler: HttpHandler<HttpHandler.Acceptance<NoParams>,
    AbstractHttpHandler.Context,
    NoParams>(NoParams) {

    override suspend fun handle(acceptance: Acceptance<NoParams>,
                                headers: Headers,
                                body: ByteBuffer,
                                context: Context): Response<*> {
      return object: Response<ByteArray>(
        Status.OK) {
        override fun bodyMediaType(body: ByteArray) = MediaType.TEXT
        override suspend fun bodyByteLength(body: ByteArray) = body.size.toLong()
        override suspend fun writeBody(socket: AsynchronousSocketChannel, buffer: ByteBuffer) {
          this.body?.let { socket.aWrite((buffer.clear() as ByteBuffer).put(it).flip() as ByteBuffer) }
        }
      }.body("Test".toByteArray(Charsets.US_ASCII))
    }

    override suspend fun context(others: Collection<*>?) = Context(others)

    override suspend fun acceptUri(method: Method, uri: String, params: NoParams): Acceptance<NoParams> {
      return Acceptance(true, false, method, uri, params)
    }

  }

  class AuthContext(others: Collection<*>?, c: AbstractHttpHandler.Context):
        AuthHandler.Context<AbstractHttpHandler.Context>(others, c) {
    val users = mapOf("user1" to "password1", "user2" to "password2")
  }

  class BasicAuthTestHandler: BasicAuthHandler<HttpHandler.Acceptance<NoParams>,
    AbstractHttpHandler.Context,
    AuthContext,
    NoParams>(HttpTestHandler(), "Test Realm") {
    override fun credentialsAreValid(auth: String, context: AuthContext): Boolean {
      return context.users.toList().find { authorizationHeaderValue(
        it.first, it.second) == auth } != null
    }

    override suspend fun context(others: Collection<*>?) = AuthContext(others, delegate.context(others))

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
      BasicAuthTestHandler()
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
          assertEquals("Test", String(it.entity.content.readBytes()))
        }
      }
    }
  }

}
