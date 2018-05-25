package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.server.http.Method
import org.junit.Assert.*
import org.junit.Test
import java.text.ParseException

class ParameterizedRouteTests {

  @Test fun testMethods() {
    val route1 = ParameterizedRoute("/test")
    assertNotNull(route1.match(Method.GET, "/test"))
    assertNotNull(route1.match(Method.HEAD, "/test"))
    assertNull(route1.match(Method.DELETE, "/test"))
    val route2 = ParameterizedRoute("/test", listOf(Method.DELETE))
    assertNotNull(route2.match(Method.DELETE, "/test"))
    assertNull(route2.match(Method.HEAD, "/test"))
    assertNull(route2.match(Method.GET, "/test"))
  }

  @Test fun testInvalid() {
    try {
      ParameterizedRoute("/test/{abc")
      fail()
    }
    catch (e: ParseException) {}
    try {
      ParameterizedRoute("/{ab!c}/test")
      fail()
    }
    catch (e: ParseException) {}
    try {
      ParameterizedRoute("/{ab{c}}/test")
      fail()
    }
    catch (e: ParseException) {}
    try {
      ParameterizedRoute("/{a}/b/{d/e}/f")
      fail()
    }
    catch (e: ParseException) {}
  }

  @Test fun testParams() {
    val route1 = ParameterizedRoute("/test")
    assertNotNull(route1.match(Method.GET, "/test"))
    route1.match(Method.GET, "/test")?.let {
      assertEquals(0, it.size)
    }
    val route2 = ParameterizedRoute("/{a}/{b}/c/{d}")
    assertNotNull(route2.match(Method.GET, "/p1/p2/c/p4"))
    route2.match(Method.GET, "/p1/p2/c/p4")?.let {
      assertEquals(3, it.size)
      assertEquals("p1", it["a"])
      assertEquals("p2", it["b"])
      assertEquals("p4", it["d"])
    }
  }

}
