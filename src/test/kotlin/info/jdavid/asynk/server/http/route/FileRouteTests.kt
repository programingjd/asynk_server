package info.jdavid.asynk.server.http.route

import info.jdavid.asynk.http.Method
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class FileRouteTests {

  @Test fun testNoPrefix() {
    val route = FileRoute(File("./src/test/resources"))
    assertNotNull(route.match(Method.GET, "/"))
    assertNotNull(route.match(Method.GET, "/index.css"))
    assertNull(route.match(Method.GET, "/doesnotexist.html"))
  }

  @Test fun testShortPrefix() {
    val route = FileRoute(File("./src/test/resources"), "/prefix")
    assertNotNull(route.match(Method.GET, "/prefix/"))
    assertNotNull(route.match(Method.GET, "/prefix/index.css"))
    assertNull(route.match(Method.GET, "/prefix/doesnotexist.html"))
    assertNull(route.match(Method.GET, "/prefix2/index.css"))
    assertNull(route.match(Method.GET, "/index.css"))
  }

  @Test fun testLongPrefix() {
    val route = FileRoute(File("./src/test/resources"), "/prefix_longer_than_full_test_uri")
    assertNotNull(route.match(Method.GET, "/prefix_longer_than_full_test_uri/"))
    assertNotNull(route.match(Method.GET, "/prefix_longer_than_full_test_uri/index.css"))
    assertNull(route.match(Method.GET, "/prefix_longer_than_full_test_uri/doesnotexist.html"))
    assertNull(route.match(Method.GET, "/index.css"))
  }

}
