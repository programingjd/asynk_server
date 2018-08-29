![jcenter](https://img.shields.io/badge/_jcenter_-0.0.0.8-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-41/41-green.png?style=flat)
# server
A server implementation in kotlin using both threads and coroutines for maximum performance.

## Download ##

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.asynk.server/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.asynk.server).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/asynk/server/0.0.0.8/server-0.0.0.8.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```
<dependency>
  <groupId>info.jdavid.asynk.server</groupId>
  <artifactId>server</artifactId>
  <version>0.0.0.8</version>
</dependency>
```
__Gradle__

Add jcenter to the list of maven repositories.
```
repositories {
  jcenter()
}
```
```
dependencies {
  compile 'info.jdavid.asynk:server:0.0.0.8'
}
```

### Usage ###

__Starting the server__

You can create and start the server by calling the ```Server``` constructor.

```
Server(genericHandler, hostAndPort, maxRequestSize) // blocks indefinitely
Server(genericHandler, hostAndPort, maxRequestSize).use {
  Thread.sleep(5000L) // stops the server when block is executed
}
```

If you don't specify **hostAndPort**, then the server will bind to **localhost:8080**.

The **maxRequestSize** is the maximum size of the request body. It defaults to **4096 bytes**.

The **genericHandler** is the function that will receive the requests and that is responsible for
sending responses back.


If what you want is an **http** server, there are helper methods for that.

```
Server.http(hostAndPort, maxRequestSize, httpHandler)
Server.http(httpHandler) // binds to localhost:8080, max request size = 4096
Server.http(httpHandler1, httpHandler2, ...)
Server.http(listOf(httpHandler1, httpHandler2, ...))
```

__Handlers__

The server uses both threads and coroutines to maximize performance.
The number of thread used is the number of cpu cores (times two with hyperthreading) minus a couple to get
headroom for other processes.
Handlers can specify a context object. There is one such object allocated for each thread, therefore, access
to this context object is thread safe.

The ```Handler``` interface is generic and not just for **http**.
A ```Handler``` needs to implement:

```
suspend fun context(others: Collection<*>?): CONTEXT
```

This is the method called to allocate the context object. If you don't need any, you can specify
```CONTEXT``` to be ```Unit``` and return ```Unit```.

Some handlers can delegate their implementation to sub handlers. In this case, the **others** collection
represents the list of already allocated context objects for the other sub handlers. This gives the
opportunity to share the context between the sub handlers (or part of it).

In the case of a simple standalone handler implementation, the **others** argument is **null**.

```
suspend fun connect(remoteAddress: InetSocketAddress): Boolean
```

This is used to accept or reject the request.

```
suspend fun handle(socket: AsynchronousSocketChannel, buffer: ByteBuffer, context: CONTEXT)
```

This is the method that actually handles the request and writes the response to the socket.
A buffer is provided and can be used to read the incoming data from the socket,
and/or write the outgoing data to the socket.
Its size is the **maxRequestSize** of the server (4096 by default).