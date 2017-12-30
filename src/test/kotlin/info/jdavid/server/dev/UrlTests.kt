package info.jdavid.server.dev

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

  @Test fun testPathSegments() {
    assertEquals(0, Url.pathSegments("http://example.com").size)
    assertEquals(0, Url.pathSegments("https://example.com:80").size)
    assertEquals(0, Url.pathSegments("https://example.com/").size)
    assertEquals(0, Url.pathSegments("https://example.com:8181/").size)
    assertEquals(2, Url.pathSegments("https://example.com:8181/some/path").size)
    assertEquals("some", Url.pathSegments("https://example.com:8181/some/path")[0])
    assertEquals("path", Url.pathSegments("https://example.com:8181/some/path")[1])
    assertEquals(2, Url.pathSegments("https://example.com:8181/some/path").size)
    assertEquals("some", Url.pathSegments("https://example.com:8181/some/path")[0])
    assertEquals("path", Url.pathSegments("https://example.com:8181/some/path")[1])
    assertEquals(2, Url.pathSegments("https://example.com:8181/some/path?query").size)
    assertEquals("some", Url.pathSegments("https://example.com:8181/some/path?query")[0])
    assertEquals("path", Url.pathSegments("https://example.com:8181/some/path?query")[1])
    assertEquals(2, Url.pathSegments("https://example.com:8181/some/path/?query#fragment").size)
    assertEquals("some", Url.pathSegments("https://example.com:8181/some/path/?query#fragment")[0])
    assertEquals("path", Url.pathSegments("https://example.com:8181/some/path/?query#fragment")[1])
    assertEquals(0, Url.pathSegments("https://example.com:8181/?query#f1/f2").size)
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
