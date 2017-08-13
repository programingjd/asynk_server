package info.jdavid.server

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class HeadersTests {

  @Test fun test1() {
    val headers =
      Headers(mutableListOf("Content-Type: text/plain", "Content-Length: 1024", "Test: 1", "test: 2"))
    testBasic(headers)
    val channel0 = AsynchronousServerSocketChannel.open()
    channel0.bind(InetSocketAddress(InetAddress.getLocalHost(), 8080))
    val channel2 = AsynchronousSocketChannel.open()
    channel2.connect(InetSocketAddress(InetAddress.getLocalHost(), 8080))
    try {
      val s1: String = runBlocking(CommonPool) {
        val channel1 = channel0.aAccept()
        headers.aWrite(channel1, Long.MAX_VALUE)
        val buffer = ByteBuffer.allocate(1024)
        val n = channel2.aRead(buffer, Long.MAX_VALUE)
        channel1.close()
        val array = buffer.array()
        String(array, 0, n, Charsets.ISO_8859_1)
      }
      //println(s1.replace("\r\n", "\\r\\n\n"))
      val split1 = s1.split("\r\n").toList()
      assertEquals(6, split1.size)
      testBasic(Headers(split1.dropLast(2).toMutableList()))
    }
    finally {
      channel2.close()
    }
  }

  private fun testBasic(headers: Headers) {
    sequenceOf("Content-Type", "CONTENT-TYPE", "content-type").forEach {
      assertTrue(headers.has(it))
      assertEquals("text/plain", headers.value(it))
      assertEquals(1, headers.values(it).size)
      assertEquals("text/plain", headers.values(it).first())
    }
    sequenceOf("Content-Length", "CONTENT-LENGTH", "content-length").forEach {
      assertTrue(headers.has(it))
      assertEquals("1024", headers.value(it))
      assertEquals(1, headers.values(it).size)
      assertEquals("1024", headers.values(it).first())
    }
    sequenceOf("Test", "TEST", "test").forEach {
      assertTrue(headers.has(it))
      assertEquals("2", headers.value(it))
      assertEquals(2, headers.values(it).size)
      assertEquals("1", headers.values(it).first())
      assertEquals("2", headers.values(it).last())
    }
    val keys = headers.keys()
    assertEquals(3, keys.size)
    assertEquals("Content-Type", keys[0])
    assertEquals("Content-Length", keys[1])
    assertEquals("Test", keys[2])
  }

}
