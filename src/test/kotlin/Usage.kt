import info.jdavid.asynk.core.asyncRead
import info.jdavid.asynk.core.asyncWrite
import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.CacheControl
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import info.jdavid.asynk.server.http.base.AuthHandler
import info.jdavid.asynk.server.http.handler.BasicAuthHandler
import info.jdavid.asynk.server.http.handler.DigestAuthHandler
import info.jdavid.asynk.server.http.handler.FileHandler
import info.jdavid.asynk.server.http.handler.HttpHandler
import info.jdavid.asynk.server.http.route.FileRoute
import info.jdavid.asynk.server.http.route.FixedRoute
import info.jdavid.asynk.server.http.route.NoParams
import info.jdavid.asynk.server.http.route.ParameterizedRoute
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

object Usage {

  @JvmStatic
  fun main(args: Array<String>) {
    echo()
  }

  fun start() {
    Server(
      HttpHandler.of(NoParams) { _, _, _, _ ->
        HttpHandler.StringResponse("Server 1", MediaType.TEXT)
      }
    )
    println("Server 1 started")

    Server.http(
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8081),
      HttpHandler.of(NoParams) { _, _, _, _ ->
        HttpHandler.StringResponse("Server 2", MediaType.TEXT)
      }
    )
    println ("Server 2 started")

    Server.http(
      InetSocketAddress(InetAddress.getLoopbackAddress(), 8082),
      HttpHandler.Builder().route(NoParams).to { _, _, _, _ ->
        HttpHandler.StringResponse("Server 3", MediaType.TEXT)
      }.build()
    ).use {
      println ("Server 3 started")
    }
    println ("Server 3 stopped")
  }

  fun echo() {
    Server(
      object : Handler<Unit> {
        override suspend fun context(others: Collection<*>?) {}
        override suspend fun connect(remoteAddress: InetSocketAddress) = true
        override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Unit) {
          while (socket.asyncRead(buffer) != -1L) {
            (buffer.flip() as ByteBuffer).apply { while (remaining() > 0) socket.asyncWrite(this) }
            buffer.flip()
          }
        }
      },
      InetSocketAddress(InetAddress.getLoopbackAddress(), 7)
    )
  }

  fun http() {
    Server(
      object : HttpHandler<HttpHandler.Acceptance<NoParams>, NoParams,
        AbstractHttpHandler.Context, NoParams>(NoParams) {
        override suspend fun context(others: Collection<*>?) = Context(others)
        override suspend fun acceptUri(method: Method, uri: String, params: NoParams): Acceptance<NoParams>? {
          return when (method) {
            Method.HEAD, Method.GET -> Acceptance(false, false, method, uri, NoParams)
            else -> null
          }
        }

        override suspend fun handle(acceptance: Acceptance<NoParams>, headers: Headers, body: ByteBuffer,
                                    context: Context) = StringResponse(
          "Method: ${acceptance.method}\r\nUri: ${acceptance.uri}",
          MediaType.TEXT
        )
      }
    ).use {}
    Server(
      HttpHandler.of(NoParams) { acceptance, _, _, _ ->
        HttpHandler.StringResponse(
          "Method: ${acceptance.method}\r\nUri: ${acceptance.uri}",
          MediaType.TEXT
        )
      }
    ).use {}
  }

  object CustomAuthValidationError: AuthHandler.ValidationError

  fun customAuth() {
    fun <ACCEPTANCE: HttpHandler.Acceptance<ACCEPTANCE_PARAMS>,
         ACCEPTANCE_PARAMS: Any,
         CONTEXT: AbstractHttpHandler.Context,
         ROUTE_PARAMS: Any> authHandler(
      delegate: HttpHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, CONTEXT, ROUTE_PARAMS>) =
      object: AuthHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, CONTEXT,
                          AuthHandler.Context<CONTEXT>,ROUTE_PARAMS, CustomAuthValidationError>(delegate) {
        val key = "k67t8MNak_Krq7_D"
        override suspend fun validateCredentials(acceptance: ACCEPTANCE, headers: Headers,
                                                 context: Context<CONTEXT>): CustomAuthValidationError? {
          val auth = headers.value(Headers.AUTHORIZATION) ?: return CustomAuthValidationError
          return if (auth == "Test $key") null else CustomAuthValidationError
        }
        override fun wwwAuthenticate(acceptance: ACCEPTANCE, headers: Headers,
                                     error: CustomAuthValidationError) = "Test realm=\"Test\""
        override suspend fun context(others: Collection<*>?) = Context(others, delegate.context(others))
      }

    Server(
      authHandler(
        HttpHandler.of(NoParams) { _, _, _, _ -> HttpHandler.EmptyResponse() }
      )
    )
  }

  fun basicAuth() {
    val credentials = mapOf(
      "user1" to "password1",
      "user2" to "password2",
      "user3" to "password3"
    )
    Server(
      BasicAuthHandler.of(
        "Test Realm",
        HttpHandler.of(NoParams) { _, _, _, _ -> HttpHandler.EmptyResponse() }
      ) { user -> credentials[user] }
    )
  }

  fun digestAuth() {
    val credentials = mapOf(
      "user1" to "password1",
      "user2" to "password2",
      "user3" to "password3"
    )
    Server(
      DigestAuthHandler.of(
        "Test Realm",
        HttpHandler.of(NoParams) { _, _, _, _ -> HttpHandler.EmptyResponse() }
      ) { user -> credentials[user] }
    )
  }

  fun files() {
    Server.http(
      object: FileHandler(
        FileRoute(File("/home/admin/challenges"), "/.well_known/acme-challenge")
      ) {
        override fun etag(file: File) = null
        override fun indexFilenames() = emptySequence<String>()
        override fun mediaType(file: File) = MediaType.TEXT
        override fun mediaTypes() = mapOf(MediaType.TEXT to CacheControl(false,
                                                                          0))
        override suspend fun acceptUri(method: Method, uri: String, params: File) =
          if (method == Method.GET) super.acceptUri(method, uri, params) else null
      },
      FileHandler(FileRoute(File("/home/admin/www")))
    )
  }

  fun routes() {
    Server(
      HttpHandler.Builder().
        handler(FileHandler(FileRoute(File("doc"), "/doc"))).
        route(FixedRoute("/healthcheck")).to { _, _, _, _ ->
          HttpHandler.StringResponse("Route 1", MediaType.TEXT)
        }.
        route(ParameterizedRoute("/{p1}/{p2}")).to { acceptance, _, _, _ ->
          val params = acceptance.routeParams
          HttpHandler.StringResponse(
            "Route 2 [p1: ${params["p1"]}, p2: ${params["p2"]}]",
            MediaType.TEXT
          )
        }.
        route(NoParams).to { _, _, _, _ ->
          HttpHandler.StringResponse("Route 3", MediaType.TEXT)
        }.
        build()
    )

  }

}
