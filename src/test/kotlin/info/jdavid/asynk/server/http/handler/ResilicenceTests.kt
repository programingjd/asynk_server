package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Server
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResilicenceTests {

  @Test
  fun testSlowResponses() {
    // /test endpoint should still be responsive even when /slow endpoint is called many times
    Server(
      HttpHandler.Builder().
        route("/test").to { _, _, _, _ ->
          HttpHandler.StringResponse("test")
        }.
        route("/slow").to { _, _, _, _ ->
          delay(300000L)
          HttpHandler.StringResponse("slow")
        }.
        build()
    ).use { server ->
      runBlocking {
        val jobs =
          (1..50).map { n ->
            n to HttpClientBuilder.create().build()
          }.map {
            val n = it.first
            val client = it.second
            GlobalScope.launch {
              client.use { client ->
                println("request $n")
                val response =
                  client.execute(HttpGet("http://${server.address.hostString}:${server.address.port}/slow"))
                response.entity.content.readBytes()
              }
            }
          }

        delay(1000L)

        assertEquals("test", async {
          HttpClientBuilder.create().build().use { client ->
            val response =
              client.execute(HttpGet("http://${server.address.hostString}:${server.address.port}/test"))
            String(response.entity.content.readBytes())
          }
        }.await())

        jobs.forEach { it.cancel() }
      }
    }
  }

  @Test
  fun testErrorResponses() {
    // /test endpoint should still be responsive even when /throwing endpoint is called many times
    Server(
      HttpHandler.Builder().
        route("/test").to { _, _, _, _ ->
        HttpHandler.StringResponse("test")
      }.
        route("/throwing").to { _, _, _, _ ->
        throw Error()
      }.
        build()
    ).use { server ->
      runBlocking {
        val jobs =
          (1..50).map { n ->
            n to HttpClientBuilder.create().build()
          }.map {
            val n = it.first
            val client = it.second
            GlobalScope.launch {
              client.use { client ->
                println("request $n")
                val response =
                  client.execute(HttpGet("http://${server.address.hostString}:${server.address.port}/throwing"))
                response.entity.content.readBytes()
              }
            }
          }

        delay(1000L)

        assertEquals("test", async {
          HttpClientBuilder.create().build().use { client ->
            val response =
              client.execute(HttpGet("http://${server.address.hostString}:${server.address.port}/test"))
            String(response.entity.content.readBytes())
          }
        }.await())

        jobs.forEach { it.cancel() }
      }
    }
  }

}
