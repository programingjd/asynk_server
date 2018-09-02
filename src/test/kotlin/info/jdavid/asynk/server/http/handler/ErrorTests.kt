package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.route.NoParams
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.nio.ByteBuffer

class ErrorTests {

  class FailingHandler: HttpHandler<HttpHandler.Acceptance<NoParams>, NoParams, AbstractHttpHandler.Context,
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
    ).use { _ ->
      val request = HttpGet().apply {
        uri = URI("http://localhost:8080")
        setHeader(Headers.USER_AGENT, "Test user agent")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request).use {
          assertEquals(500, it.statusLine.statusCode)
        }
      }
    }
  }

}
