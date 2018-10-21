package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.http.Method
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.IllegalArgumentException

class FixedRouteTests {

  @Test fun testMethods() {
    val route1 = FixedRoute("/test")
    assertNotNull(route1.match(Method.GET, "/test"))
    assertNotNull(route1.match(Method.HEAD, "/test"))
    assertNull(route1.match(Method.DELETE, "/test"))
    val route2 = FixedRoute("/test", 4096, listOf(Method.DELETE))
    assertNotNull(route2.match(Method.DELETE, "/test"))
    assertNull(route2.match(Method.HEAD, "/test"))
    assertNull(route2.match(Method.GET, "/test"))
  }

  @Test fun testInvalid() {
    try {
      FixedRoute("")
      fail<Nothing>()
    }
    catch (e: IllegalArgumentException) {}
  }

  @Test fun testAbsolute() {
    val route1 = FixedRoute("/test")
    assertEquals(NoParams, route1.match(Method.GET, "/test"))
    assertNull(route1.match(Method.GET, "/a/test"))
  }

  @Test fun testRelative() {
    val route1 = FixedRoute("test")
    assertEquals(NoParams, route1.match(Method.GET, "/test"))
    assertEquals(NoParams, route1.match(Method.GET, "/a/test"))
    assertNull(route1.match(Method.GET, "/test1"))
    assertNull(route1.match(Method.GET, "/a/test1"))
    assertNull(route1.match(Method.GET, "/a/test/b"))
  }

}
