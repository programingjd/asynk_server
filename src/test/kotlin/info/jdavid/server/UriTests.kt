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

  @Test fun testParent() {
    assertEquals("https://example.com/", Uri.parent("https://example.com/a"))
    assertEquals("http://example.com/a/", Uri.parent("http://example.com/a/b"))
    assertEquals("http://example.com/?q=1", Uri.parent("http://example.com/a?q=1"))
    assertEquals("http://example.com/#/p1/p2", Uri.parent("http://example.com/a#/p1/p2"))
    assertEquals("http://example.com/a/?q=1#h", Uri.parent("http://example.com/a/b?q=1#h"))
  }

  @Test fun testLastPathSegment() {
    assertEquals("", Uri.lastPathSegment("https://example.com/"))
    assertEquals("", Uri.lastPathSegment("https://example.com/a/"))
    assertEquals("a", Uri.lastPathSegment("https://example.com/a"))
    assertEquals("b", Uri.lastPathSegment("http://example.com/a/b"))
    assertEquals("a", Uri.lastPathSegment("http://example.com/a?q=1"))
    assertEquals("a", Uri.lastPathSegment("http://example.com/a#/p1/p2"))
    assertEquals("b", Uri.lastPathSegment("http://example.com/a/b?q=1#h"))
  }

}
