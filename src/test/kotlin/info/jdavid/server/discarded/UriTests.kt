package info.jdavid.server.discarded

import info.jdavid.server.discarded.http.Uri
import org.junit.Assert
import org.junit.Test

class UriTests {

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
