package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.route.NoParams
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.IOException
import java.lang.RuntimeException

class StartStopTests {

  @Test
  fun testUse() {
    HttpClientBuilder.create().build().use { client ->
      val address = Server(
        HttpHandler.of(NoParams) { _, _, _, _ -> HttpHandler.StringResponse("test") }
      ).use { server ->
        val text = client.execute(
          HttpGet("http://${server.address.hostString}:${server.address.port}")
        ).let { String(it.entity.content.readBytes()) }
        assertEquals("test", text)
        server.address
      }
      try {
        client.execute(
          HttpGet("http://${address.hostString}:${address.port}")
        )
        fail<Nothing>()
      }
      catch (ignore: IOException) {}
    }
  }

  @Test
  fun testUseWithException() {
    HttpClientBuilder.create().build().use { client ->
      val address = Server(
        HttpHandler.of(NoParams) { _, _, _, _ -> throw RuntimeException() }
      ).use { server ->
        val status = client.execute(
          HttpGet("http://${server.address.hostString}:${server.address.port}")
        ).use { it.statusLine.statusCode }
        assertEquals(500, status)
        server.address
      }
      try {
        client.execute(
          HttpGet("http://${address.hostString}:${address.port}")
        )
        fail<Nothing>()
      }
      catch (ignore: IOException) {}
    }
  }

}
