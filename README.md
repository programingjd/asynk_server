![jcenter](https://img.shields.io/badge/_jcenter_-0.0.0.8.0-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-41/41-green.png?style=flat)
# server
A TCP server implementation in kotlin that uses both threads and coroutines for maximum performance.  
It supports generic TCP handlers, but also HTTP with routing, authentication, etc...  
HTTPS and HTTP2 are not implemented. It is meant to run behind a reverse proxy or load balancer that can take care of those two aspects
([NGINX](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/) or 
 [H2O](https://h2o.examp1e.net/configure/proxy_directives.html) for instance).


## Download ##

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.asynk.server/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.asynk.server).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/asynk/server/0.0.0.8.0/server-0.0.0.8.0.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```maven
<dependency>
  <groupId>info.jdavid.asynk.server</groupId>
  <artifactId>server</artifactId>
  <version>0.0.0.8.0</version>
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
  compile 'info.jdavid.asynk:server:0.0.0.8.0'
}
```

### Usage ###

  + [Starting and stopping the server](#starting_and_stopping)
  + [Logging](#logging)
  + [Handlers](#handlers)
    + [Context](#context)
    + [TCP Handlers](#tcp_handlers)
    + [HTTP Handlers](#http_handlers)
    + [HTTP Authentication Handlers](#auth_handlers)
    + [File Handlers](#file_handlers)
  + [Routing and combining handlers]()

[__Starting and stopping the server__](#starting_and_stopping)

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


[__Logging__](#logging)

This library uses [SLF4J](https://www.slf4j.org/) 1.7.25 for logging.
If you don't want any logs, you can add the [slf4j-nop](https://mvnrepository.com/artifact/org.slf4j/slf4j-nop)
dependency.
If you do want logs, you can add a dependency on any lsf4j implementation 
([slf4j-jdk14](https://mvnrepository.com/artifact/org.slf4j/slf4j-jdk14) for instance).  
The server itself only logs errors. It's the responsibility of handlers to log requests if they want to.
The default implementations of HTTP handlers log both the remote address and uri of every incoming request.


[__Handlers__](#handlers)

Handlers are responsible for reading the incoming requests and sending back a response.

[_Context_](#context)

The server uses both threads and coroutines to maximize performance.
Handlers can define a `Context` object. This object will be shared between all instances of the handler
running on the same thread, but only on the same thread. This context object enables the sharing of resources
without having to worry about thread safety.

[_TCP Handlers_](#tcp_handlers)

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

