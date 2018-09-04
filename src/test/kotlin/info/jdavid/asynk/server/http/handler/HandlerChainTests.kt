package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.Server
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI

class HandlerChainTests {

  @Test fun testSimple() {
    val range = (1..5)
    val handlers = range.map {
      HttpHandler.of(
        NumberedRoute(it)
        ) { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }
    }
    Server.http(
      handlers
    ).use { _ ->
      range.forEach { n ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${n}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            assertEquals(it.getLastHeader(
              Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
            assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
            assertEquals(n.toString(), String(bytes))
          }
        }
      }
    }
  }

  @Test fun testBuilder() {
    Server.http(
      HttpHandler.Builder().
        route(NumberedRoute(0)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        route(NumberedRoute(2)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        route(NumberedRoute(3)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        build(),
      HttpHandler.of(
        NumberedRoute(5)
      ) { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        },
      HttpHandler.of(
        NumberedRoute(2)
      ) { _ , _, _, _ ->
        HttpHandler.StringResponse("#", MediaType.TEXT)
      }
    ).use {
      listOf(0, 2, 3, 5).forEach { n ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${n}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
            assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
            assertEquals(n.toString(), String(bytes))
          }
        }
      }
      listOf("1", "4", "6", "test", "2/3").forEach { path ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${path}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(404, it.statusLine.statusCode, path)
          }
        }
      }
    }
  }

  private class NumberedRoute(val number: Int): HttpHandler.Route<Int> {
    override fun match(method: Method, uri: String): Int? {
      return if (Uri.path(uri) == "/${number}") number else null
    }
  }

}
