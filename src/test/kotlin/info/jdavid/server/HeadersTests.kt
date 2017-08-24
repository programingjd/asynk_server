package info.jdavid.server

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

class HeadersTests {

  @Test fun testFromList() {
    val headers =
      Headers(mutableListOf("Content-Type: text/plain", "Content-Length: 1024", "Test: 1", "test: 2"))
    test(headers)
    val channel0 = AsynchronousServerSocketChannel.open()
    channel0.bind(InetSocketAddress(InetAddress.getLocalHost(), 8080))
    val channel2 = AsynchronousSocketChannel.open()
    channel2.connect(InetSocketAddress(InetAddress.getLocalHost(), 8080))
    val nodes = LockFreeLinkedListHead()
    try {
      val s1: String = runBlocking(CommonPool) {
        val channel1 = channel0.aAccept()
        val channel = InsecureChannel(channel1, nodes, 8192)
        channel.write(headers, Long.MAX_VALUE)
        val buffer = ByteBuffer.allocate(1024)
        val n = channel2.aRead(buffer, Long.MAX_VALUE)
        channel1.close()
        val array = buffer.array()
        String(array, 0, n, Charsets.ISO_8859_1)
      }
      //println(s1.replace("\r\n", "\\r\\n\n"))
      val split1 = s1.split("\r\n").toList()
      assertEquals(6, split1.size)
      test(Headers(split1.dropLast(2).toMutableList()))
    }
    finally {
      channel2.close()
    }
  }

  @Test fun testAdd() {
    val headers = Headers()
    assertEquals(0, headers.keys().size)
    assertFalse(headers.has("Content-Type"))
    assertNull(headers.value("Content-Type"))
    assertEquals(0, headers.values("Content-Type").size)
    headers.add("Content-Type", "text/plain")
    assertTrue(headers.has("Content-Type"))
    assertEquals("text/plain", headers.value("Content-Type"))
    assertEquals(1, headers.values("Content-Type").size)
    assertFalse(headers.has("Content-Length"))
    assertNull(headers.value("Content-Length"))
    assertEquals(0, headers.values("Content-Length").size)
    headers.add("Content-Length: 1024")
    assertTrue(headers.has("Content-Type"))
    assertEquals("text/plain", headers.value("Content-Type"))
    assertEquals(1, headers.values("Content-Type").size)
    assertTrue(headers.has("Content-Length"))
    assertEquals("1024", headers.value("Content-Length"))
    assertEquals(1, headers.values("Content-Length").size)
    headers.add("Test", "1")
    headers.add("test: 2")
    test(headers)
  }

  private fun test(headers: Headers) {
    assertFalse(headers.has("ContentType"))
    assertNull(headers.value("ContentType"))
    assertEquals(0, headers.values("ContentType").size)
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
