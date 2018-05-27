package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.http.MediaType
import org.junit.Test

class BuilderTests {

  @Test
  fun test1() {
    val handler = HttpHandler.Builder().
      route().fixed("/fixed").handle({ _ , _, _, _ ->
        HttpHandler.StringResponse("fixed", MediaType.TEXT)
      }).
      route().parameterized("/param/{p1}").handle({ acceptance , _, _, _ ->
        HttpHandler.StringResponse(acceptance.routeParams?.get("p1") ?: "?", MediaType.TEXT)
      }).
      build()
  }

}
