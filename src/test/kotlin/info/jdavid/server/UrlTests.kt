package info.jdavid.server

import info.jdavid.server.http.Url
import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalArgumentException

class UrlTests {

  @Test fun testScheme() {
    assertEquals("http", Url.scheme("http://example.com"))
    assertEquals("https", Url.scheme("https://www.example.com/some/path"))
    assertEquals("ftp", Url.scheme("ftp://example.com/"))
    assertEquals("http", Url.scheme("http://example.com:8080"))
    assertEquals("https", Url.scheme("https://www.example.com:8181/dir/"))
    try {
      Url.scheme("http://")
      fail()
    }
    catch (e: IllegalArgumentException) {}
    try {
      Url.scheme("http:/example.com")
      fail()
    }
    catch (e: IllegalArgumentException) {}
  }

  @Test fun testAuthority() {
    assertEquals("example.com", Url.authority("http://example.com"))
    assertEquals("example.com", Url.authority("http://example.com/"))
    assertEquals("example.com", Url.authority("https://example.com/some/path"))
    assertEquals("example.com:8181", Url.authority("https://example.com:8181/dir/"))
    try {
      Url.authority("http://")
      fail()
    }
    catch (e: IllegalArgumentException) {}
    try {
      Url.authority("http:/example.com")
      fail()
    }
    catch (e: IllegalArgumentException) {}
  }

  @Test fun testPath() {
    assertEquals("", Url.path("http://example.com"))
    assertEquals("", Url.path("https://example.com:80"))
    assertEquals("/", Url.path("https://example.com/"))
    assertEquals("/", Url.path("https://example.com:8181/"))
    assertEquals("/some/path", Url.path("https://example.com:8181/some/path"))
    assertEquals("/some/path/", Url.path("https://example.com:8181/some/path/"))
    assertEquals("/some/path", Url.path("https://example.com:8181/some/path?query"))
    assertEquals("/some/path/", Url.path("https://example.com:8181/some/path/?query#fragment"))
    assertEquals("/", Url.path("https://example.com:8181/?query#f1/f2"))
  }

  @Test fun testHttps() {
    assertEquals("https://example.com", Url.https("http://example.com"))
    assertEquals("https://example.com", Url.https("https://example.com"))
    assertEquals("https://example.com/some/path", Url.https("http://example.com/some/path"))
    assertEquals("https://example.com", Url.https("http://example.com:123"))
    assertEquals("https://example.com:443", Url.https("http://example.com:80"))
    assertEquals("https://example.com:443/", Url.https("http://example.com:80/"))
    assertEquals("https://example.com:8181", Url.https("http://example.com:8080"))
  }

  @Test fun testParent() {
    assertEquals("https://example.com/", Url.parent("https://example.com/a"))
    assertEquals("http://example.com/a/", Url.parent("http://example.com/a/b"))
    assertEquals("http://example.com/?q=1", Url.parent("http://example.com/a?q=1"))
    assertEquals("http://example.com/#/p1/p2", Url.parent("http://example.com/a#/p1/p2"))
    assertEquals("http://example.com/a/?q=1#h", Url.parent("http://example.com/a/b?q=1#h"))
  }

  @Test fun testLastPathSegment() {
    assertEquals("", Url.lastPathSegment("https://example.com/"))
    assertEquals("", Url.lastPathSegment("https://example.com/a/"))
    assertEquals("a", Url.lastPathSegment("https://example.com/a"))
    assertEquals("b", Url.lastPathSegment("http://example.com/a/b"))
    assertEquals("a", Url.lastPathSegment("http://example.com/a?q=1"))
    assertEquals("a", Url.lastPathSegment("http://example.com/a#/p1/p2"))
    assertEquals("b", Url.lastPathSegment("http://example.com/a/b?q=1#h"))
  }

}
