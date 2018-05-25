![jcenter](https://img.shields.io/badge/_jcenter_-1.0.0.2-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-28/28-green.png?style=flat)
# server
An http/http2 server in kotlin using both threads and coroutines for maximum performance.

## Download ##

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.asynk.server/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.asynk.server).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/server/server/1.0.0/server-1.0.0.2.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```
<dependency>
  <groupId>info.jdavid.asynk.server</groupId>
  <artifactId>server</artifactId>
  <version>1.0.0.2</version>
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
  compile 'info.jdavid.asynk.server:server:1.0.0.2'
}
```
