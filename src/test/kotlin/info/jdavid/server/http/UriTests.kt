package info.jdavid.server.http

import org.junit.Assert
import org.junit.Test

class UriTests {

  @Test
  fun testQuery() {
    Assert.assertNull(Uri.query("/"))
    Assert.assertNull(Uri.query("/some/path"))
    Assert.assertNull(Uri.query("/some/path/"))
    Assert.assertNull(Uri.query("/some/path#fragment"))
    Assert.assertEquals(0, Uri.query("/some/path?")?.size)
    Assert.assertEquals(0, Uri.query("/some/path?#fragment")?.size)
    Assert.assertEquals("", Uri.query("/some/path?1")?.get("1"))
    Assert.assertEquals("d", Uri.query("/some/path/?abc=d")?.get("abc"))
    Assert.assertEquals("", Uri.query("/some/path?q#fragment")?.get("q"))
    Assert.assertNull("", Uri.query("/some/path?q#fragment")?.get("abc"))
    Assert.assertEquals("1", Uri.query("/some/path?a=1&b=2#fragment")?.get("a"))
    Assert.assertEquals("2", Uri.query("/some/path?a=1&b=2#fragment")?.get("b"))
  }

  @Test
  fun testFragment() {
    Assert.assertNull(Uri.fragment("/"))
    Assert.assertNull(Uri.fragment("/some/path"))
    Assert.assertNull(Uri.fragment("/some/path/"))
    Assert.assertNull(Uri.fragment("/some/path?query"))
    Assert.assertEquals("", Uri.fragment("/some/path#"))
    Assert.assertEquals("", Uri.fragment("/some/path/#"))
    Assert.assertEquals("", Uri.fragment("/some/path?query#"))
    Assert.assertEquals("abc", Uri.fragment("/some/path#abc"))
    Assert.assertEquals("1", Uri.fragment("/some/path/#1"))
    Assert.assertEquals("@here", Uri.fragment("/some/path?query#@here"))
  }

  @Test
  fun testPath() {
    Assert.assertEquals("/", Uri.path("/"))
    Assert.assertEquals("/some/path", Uri.path("/some/path"))
    Assert.assertEquals("/some/path/", Uri.path("/some/path/"))
    Assert.assertEquals("/some/path", Uri.path("/some/path?query"))
    Assert.assertEquals("/some/path/", Uri.path("/some/path/?query#fragment"))
    Assert.assertEquals("/", Uri.path("/?query#f1/f2"))
  }

  @Test
  fun testParent() {
    Assert.assertEquals("/", Uri.parent("/a"))
    Assert.assertEquals("/a/", Uri.parent("/a/b"))
    Assert.assertEquals("/?q=1", Uri.parent("/a?q=1"))
    Assert.assertEquals("/#/p1/p2", Uri.parent("/a#/p1/p2"))
    Assert.assertEquals("/a/?q=1#h", Uri.parent("/a/b?q=1#h"))
  }

  @Test
  fun testLastPathSegment() {
    Assert.assertEquals("", Uri.lastPathSegment("/"))
    Assert.assertEquals("", Uri.lastPathSegment("/a/"))
    Assert.assertEquals("a", Uri.lastPathSegment("/a"))
    Assert.assertEquals("b", Uri.lastPathSegment("/a/b"))
    Assert.assertEquals("a", Uri.lastPathSegment("/a?q=1"))
    Assert.assertEquals("a", Uri.lastPathSegment("/a#/p1/p2"))
    Assert.assertEquals("b", Uri.lastPathSegment("/a/b?q=1#h"))
  }

}
