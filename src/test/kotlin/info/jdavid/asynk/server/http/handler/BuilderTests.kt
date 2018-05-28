package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.http.MediaType
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.route.FixedRoute
import org.junit.Test

class BuilderTests {

  @Test
  fun test1() {
    val handler = HttpHandler.Builder().
      route("/fixed").to({ _ , _, _, _ ->
        HttpHandler.StringResponse("fixed", MediaType.TEXT)
      }).
      route("/param/{p1}").to({ acceptance, _, _, _ ->
        HttpHandler.StringResponse(acceptance.routeParams?.get("p1") ?: "?", MediaType.TEXT)
      }).
      route(object: HttpHandler.Route<Boolean> {
        override fun match(method: Method, uri: String) = true)
      }).to({ acceptance, _, _, _ ->
        HttpHandler.StringResponse(acceptance.routeParams?.toString(), MediaType.TEXT)
      }).
      handler(HttpHandler.of(
        FixedRoute("/handler"),
        { _ , _, _, _ ->
          HttpHandler.StringResponse("handler", MediaType.TEXT)
        }
      )).
      build()
  }

}
