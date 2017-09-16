package info.jdavid.server

import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalArgumentException

class UriTests {

  @Test fun testScheme() {
    assertEquals("http", Uri.scheme("http://example.com"))
    assertEquals("https", Uri.scheme("https://www.example.com/some/path"))
    assertEquals("ftp", Uri.scheme("ftp://example.com/"))
    assertEquals("http", Uri.scheme("http://example.com:8080"))
    assertEquals("https", Uri.scheme("https://www.example.com:8181/dir/"))
    try {
      Uri.scheme("http://")
      fail()
    }
    catch (e: IllegalArgumentException) {}
    try {
      Uri.scheme("http:/example.com")
      fail()
    }
    catch (e: IllegalArgumentException) {}
  }

  @Test fun testAuthority() {
    assertEquals("example.com", Uri.authority("http://example.com"))
    assertEquals("example.com", Uri.authority("http://example.com/"))
    assertEquals("example.com", Uri.authority("https://example.com/some/path"))
    assertEquals("example.com:8181", Uri.authority("https://example.com:8181/dir/"))
    try {
      Uri.authority("http://")
      fail()
    }
    catch (e: IllegalArgumentException) {}
    try {
      Uri.authority("http:/example.com")
      fail()
    }
    catch (e: IllegalArgumentException) {}
  }

  @Test fun testPath() {
    assertEquals("", Uri.path("http://example.com"))
    assertEquals("", Uri.path("https://example.com:80"))
    assertEquals("/", Uri.path("https://example.com/"))
    assertEquals("/", Uri.path("https://example.com:8181/"))
    assertEquals("/some/path", Uri.path("https://example.com:8181/some/path"))
    assertEquals("/some/path/", Uri.path("https://example.com:8181/some/path/"))
    assertEquals("/some/path", Uri.path("https://example.com:8181/some/path?query"))
    assertEquals("/some/path/", Uri.path("https://example.com:8181/some/path/?query#fragment"))
    assertEquals("/", Uri.path("https://example.com:8181/?query#f1/f2"))
  }

  @Test fun testHttps() {
    assertEquals("https://example.com", Uri.https("http://example.com"))
    assertEquals("https://example.com", Uri.https("https://example.com"))
    assertEquals("https://example.com/some/path", Uri.https("http://example.com/some/path"))
    assertEquals("https://example.com", Uri.https("http://example.com:123"))
    assertEquals("https://example.com:443", Uri.https("http://example.com:80"))
    assertEquals("https://example.com:443/", Uri.https("http://example.com:80/"))
    assertEquals("https://example.com:8181", Uri.https("http://example.com:8080"))
  }

}
