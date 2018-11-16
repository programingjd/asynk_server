![jcenter](https://img.shields.io/badge/_jcenter_-0.0.0.22-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-31/31-green.png?style=flat)
# Asynk Server
A TCP server implementation in kotlin that uses both threads and coroutines for maximum performance.  
It supports generic TCP handlers, but also HTTP with routing, authentication, etc...  
HTTPS and HTTP2 are not implemented. It is meant to run behind a reverse proxy or load balancer that can take care of those two aspects
([NGINX](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/) or 
 [H2O](https://h2o.examp1e.net/configure/proxy_directives.html) for instance).


## Download

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.asynk.server/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.asynk.server).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/asynk/server/0.0.0.22/server-0.0.0.22.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```maven
<dependency>
  <groupId>info.jdavid.asynk</groupId>
  <artifactId>server</artifactId>
  <version>0.0.0.22</version>
</dependency>
```
__Gradle__

Add jcenter to the list of maven repositories.
```gradle
repositories {
  jcenter()
}
```
```gradle
dependencies {
  compile 'info.jdavid.asynk:server:0.0.0.22'
}
```

## Usage

  + [Starting and stopping the server](#starting-and-stopping-the-server)
  + [Logging](#logging)
  + [Handlers](#handlers)
    + [Context](#context)
    + [TCP Handlers](#tcp-handlers)
    + [HTTP Handlers](#http-handlers)
    + [HTTP Authentication Handlers](#http-authentication-handlers)
    + [File Handlers](#file-handlers)
  + [Routing and combining handlers](#routing-and-combining-handlers)

---

### Starting and stopping the server

You can create and start the server by calling the `Server` constructor.

```kotlin
@JvmStatic
fun main(args: Array<String>) {
  Server(
    HttpHandler.of(NoParams) { _, _, _, _ ->
      HttpHandler.StringResponse("Server 1", MediaType.TEXT)
    }
  )
}
```

The JVM will not stop until the server is stopped.

Rather than calling the constructor directly, there are helper methods when using http handlers.

```kotlin
val hostAndPort = InetSocketAddress(InetAddress.getLoopbackAddress(), 8081)
val maxRequestSize = 16384
Server.http(
  hostAndPort,
  maxRequestSize,
  HttpHandler.of(NoParams) { _, _, _, _ ->
    HttpHandler.StringResponse("Server 2", MediaType.TEXT)
  }
)
```

If you don't specify **hostAndPort**, then the server will bind to **localhost:8080**.  
The **maxRequestSize** is the maximum size of the request body. It defaults to **4096 bytes**.

`Server` implements `Closable` and you can stop it by calling the `close()` method.

```kotlin
Server.http(
  InetSocketAddress(InetAddress.getLoopbackAddress(), 8082),
  HttpHandler.Builder().
    route(NoParams).to { _, _, _, _ ->
    HttpHandler.StringResponse("Server 3", MediaType.TEXT)
  }.build()
).use {
  Thread.sleep(15000) // The server will stop after the block returns.
}
```

---

### Logging

This library uses [SLF4J](https://www.slf4j.org/) 1.7.25 for logging.
If you don't want any logs, you can add the [slf4j-nop](https://mvnrepository.com/artifact/org.slf4j/slf4j-nop)
dependency.
If you do want logs, you can add a dependency on any lsf4j implementation 
([slf4j-jdk14](https://mvnrepository.com/artifact/org.slf4j/slf4j-jdk14) for instance).  
The server itself only logs errors. It's the responsibility of handlers to log requests if they want to.
The default implementations of HTTP handlers log both the remote address and uri of every incoming request.

---

### Handlers

Handlers are responsible for reading the incoming requests and sending back a response.

<br>
   
#### Context

The server uses both threads and coroutines to maximize performance.
Handlers can define a `Context` object. This object will be shared between all instances of the handler
running on the same thread, but only on the same thread. This context object enables the sharing of resources
without having to worry about thread safety.

<br>

#### TCP Handlers

A generic TCP `Handler` has to implement 3 methods.  
The first is the method that returns a new context object.   
The second is a method that accepts the connection.   
The third is the handle method itself, responsible for reading the request and sending back the response.
The server dispatcher keeps a pool of `ByteBuffer` and supplies one for each call to the `handle` method.

Here's a simple implementation of an [Echo](https://tools.ietf.org/html/rfc862) server.
```kotlin
Server(
  object: Handler<Unit> {
    override suspend fun context(others: Collection<*>?) {}
    override suspend fun connect(remoteAddress: InetSocketAddress) = true
    override suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: Unit) {
      while (socket.aRead(buffer) != -1) {
        socket.aWrite(buffer.flip() as ByteBuffer)
        buffer.flip()
      }
    }
  },
  InetSocketAddress(InetAddress.getLoopbackAddress(), 7)
)
```

<br>

#### HTTP Handlers

An `HttpHandler` needs to implement 3 methods.  
The first is the method that returns a new context object.   
The second is a method that accepts the request based on its http method and uri.  
The third is the handle method itself, responsible for returning a response object.   
The handle method can get the request method and uri from the acceptance object returned by the second method. 
The request headers, the request body (which can be empty) and the context are also available.

The constructor of `HttpHandler` takes a `Route` object that can restrict which request are processed by the
handler. The special `NoParams` route accepts all requests.

```kotlin
Server(
  object: HttpHandler<HttpHandler.Acceptance<NoParams>, NoParams,
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
)
```

For a less verbose way of creating the same handler, you can make use of the helper method `HttpHandler.of`.

```kotlin
Server(
  HttpHandler.of(NoParams) { acceptance, _, _, _ ->
    HttpHandler.StringResponse(
      "Method: ${acceptance.method}\r\nUri: ${acceptance.uri}",
      MediaType.TEXT
    )
  }
)
```

The first syntax is useful when you need a custom `Context` object, or `Acceptance` object.   
Here's an example of such usage:  
The context stores a list of api keys. If its been too long since it last updated the list, then
it goes and reads it from the database.
Note that all instances of `KeysContext` that reside on the same thread are sharing the list of keys.  
The `Acceptance` class captures the api key from the uri, and the `handle` method can use the context to
make sure the key is in the list of authorized keys.

```kotlin
val databaseName = "dbname"
val username = "api"
val password = "q6vQU?WXWu^gnDS#"
val getKeysFromDatabase = suspend {
  MysqlAuthentication.Credentials.PasswordCredentials(username, password).connectTo(databaseName).use {
    it.rows("SELECT key FROM keys").toList().map { it["key"] as String }
  }.toSet()
}
val keys = runBlocking { getKeysFromDatabase() }

class KeysContext(others: Collection<*>?): AbstractHttpHandler.Context(others) {
  var keys: Set<String> = (others?.find { it is KeysContext } as? KeysContext)?.keys ?: keys
  private set

  private var lastUpdate: Long =
    (others?.find { it is KeysContext } as? KeysContext)?.lastUpdate ?: System.currentTimeMillis()

  suspend fun updateIfNecessary() {
    val now = System.currentTimeMillis()
    if (now - lastUpdate > 1000*60*60) {
      this.keys = getKeysFromDatabase()
      lastUpdate = now
    }
  }
}

class KeyAcceptance(method: Method, uri: String, val key: String):
  HttpHandler.Acceptance<String>(true, false, method, uri, key)

abstract class KeysHttpHandler<PARAMS: Any>(route: Route<PARAMS>):
  HttpHandler<KeyAcceptance, String, KeysContext, PARAMS>(route) {
  override suspend fun context(others: Collection<*>?) = KeysContext(others)
  override suspend fun acceptUri(method: Method, uri: String, params: PARAMS) =
    Uri.query(uri)?.get("key")?.let { KeyAcceptance(method, uri, it) }
  final override suspend fun handle(acceptance: KeyAcceptance, headers: Headers, body: ByteBuffer,
                                    context: KeysContext): Response<*> {
    context.updateIfNecessary()
    return if (context.keys.contains(acceptance.key)) {
      handle(acceptance.method, acceptance.uri, headers, body)
    } else AuthHandler.UnauthorizedResponse()
  }
  abstract fun handle(method: Method, uri: String, headers: Headers, body: ByteBuffer): Response<*>
}

fun <PARAMS: Any> handler(
  route: HttpHandler.Route<PARAMS>,
  handler: (method: Method, uri: String, headers: Headers, body: ByteBuffer) -> HttpHandler.Response<*>
) = object: KeysHttpHandler<PARAMS>(route) {
  override fun handle(method: Method, uri: String, headers: Headers, body: ByteBuffer) =
    handler(method, uri, headers, body)
}

Server.http(
  handler(FixedRoute("/test1", listOf(Method.GET))) { _, _, _, _ -> HttpHandler.EmptyResponse() },
  handler(FixedRoute("/test2", listOf(Method.GET))) { _, _, _, _ -> HttpHandler.EmptyResponse() }
)
```

<br>

#### HTTP Authentication Handlers

For uris within protected spaces requiring authentication, the following flow is used:

 1) The client requests the uri.
 2) The server responds with a 401 status code and with a **WWW-Authenticate** header describing what
  kind of credentials are allowed.
 3) The client requests the uri again, but this time it also sends the credentials in the **Authorization**
  header.
 4) The server validates the credentials and either completes the requests if they are correct, and sends
  another 401 response. 

An `AuthHandler` is given a delegate handler in its constructor. That delegate is responsible for handling
requests when the credentials are valid.   
`AuthHandler` implementations also need to implement 3 methods.   
The first is the method that checks the value of the **Authorization** header field and returns an error 
or null if the credentials are valid.     
The second is the method that returns the **WWW-Authenticate** header value based on the error.   
The third is the method that returns the context. It usually only returns a context that wraps the context of
the delegate.

Here's an example that uses a simple (and very unsafe) key string. It sends `Test realm="Test"` for the
**WWW-Authenticate** header field value, and only accepts `Test k67t8MNak_Krq7_D` for the 
**Authorization** header field value.

```kotlin
fun <ACCEPTANCE: HttpHandler.Acceptance<ACCEPTANCE_PARAMS>,
     ACCEPTANCE_PARAMS: Any,
     CONTEXT: AbstractHttpHandler.Context,
     ROUTE_PARAMS: Any> authHandler(
  delegate: HttpHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, CONTEXT, ROUTE_PARAMS>) =
  object: AuthHandler<ACCEPTANCE, ACCEPTANCE_PARAMS, CONTEXT,
                      AuthHandler.Context<CONTEXT>,ROUTE_PARAMS,CustomAuthValidationError>(delegate) {
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
```

The most common type of HTTP authentication is the Basic Authentication scheme. However it sends the password
in clear-text which makes it very insecure for unencrypted traffic. The Digest Authentication scheme is also
a password-base authentication, but it doesn't send the password in clear-text.   
There are ready to use implementation for both of those authentication schemes.

Basic Authentication:

```kotlin
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
```

Digest Authentication:

```kotlin
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
```

<br>

#### File Handlers

A `FileHandler` is used to serve files under a directory root.   
You can customize many things, including the directory index file names, the accepted media types and 
their cache policies as well as the ETag generation used for client caching.

```kotlin
Server.http(
  object: FileHandler(
    FileRoute(File("/home/admin/challenges"), "/.well_known/acme-challenge")
  ) {
    override fun etag(file: File) = null
    override fun indexFilenames() = emptySequence<String>()
    override fun mediaType(file: File) = MediaType.TEXT
    override fun mediaTypes() = mapOf(MediaType.TEXT to MediaType.CacheControl(false, 0))
    override suspend fun acceptUri(method: Method, uri: String, params: File) =
      if (method == Method.GET) super.acceptUri(method, uri, params) else null
  },
  FileHandler(FileRoute(File("/home/admin/www")))
)
```

---

### Routing and combining handlers

An `HttpHandler` is attached to a `Route` (it is passed to the constructor).   
The `Route` interface has only one method `match` that given a request method and uri, returns whether the
request can be handled by the route or not. If it does, then it returns a parameter object. If it doesn't,
it simply returns null.
There are different implementations of `Route` with different parameter types.
If you want to create a custom but don't need to return any parameter, you should return `NoParam`.

`NoParam` is a route that accepts all requests and returns itself as a parameter.

`FixedRoute` is a route that only accepts uris matching a given path.

`ParameterizedRoute` is a like a FixedRoute, but instead of only having fixed path segments, variables can
be used and the values will be captured in a map that will be returned as the route parameter.

`FileRoute` matches all the paths representing existing files under the specified root directory.

Here are a few examples.

```kotlin
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
```

As the example shows, `HttpHandler.Builder` can be used to combine different handlers on different routes
together into one single handler. The order is important because the first match will be used. For example,
in the example, if the last handler with the `NoParams` had been defined first, then the other ones would
never be used since `NoParams` matches every request.

There are also helper `Server.http` methods that can be used to combine multiple http handlers together.


# Other libraries

One of the main benefits of the Asynk Server implementation is that the handler is a suspendable function.
To take full advantage of this, IO operations should also be using suspendable functions.   
Unfortunatly, [JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity) is a blocking API and this
makes using suspendable calls to a database difficult.   
That's why the Asynk family of libraries includes suspending implementations of some of the most used
databases.

 + [asynk_mysql](https://github.com/programingjd/asynk_mysql) can be used to work with both MySQL and MariaDB.
 + [asynk_postgres](https://github.com/programingjd/asynk_posgres) can be used to work with PostgreSQL.

Both of those use common interfaces defined in [asynk_sql](https://github.com/programingjd/asynk_sql) that
make it easy to migrate from one database to another with minimum code change.

For [NoSQL](https://en.wikipedia.org/wiki/NoSQL) databases, they usually come with better a API that supports
asynchronous calls that can be wrapped easily into suspendable functions. This is the case for 
[MongoDB](https://github.com/mongodb/mongo-java-driver/tree/master/driver-async) for instance.

<br>