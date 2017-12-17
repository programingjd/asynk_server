package info.jdavid.server.dev

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
  val server = Server()
  val counter = AtomicInteger()
  val executors = Executors.newFixedThreadPool(32)
  for (i in 0..1000) {
    executors.submit {
      val conn = URL("http://localhost:8080").openConnection() as HttpURLConnection
      conn.useCaches = false
      try {
        val bytes = conn.inputStream.readBytes(512)
        if (String(bytes) == "Test") println(i)
      }
      finally {
        conn.disconnect()
      }
    }
  }
  executors.shutdown()
  executors.awaitTermination(15000L, TimeUnit.MILLISECONDS)
  server.stop()
}
