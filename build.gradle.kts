import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    jcenter()
  }
}

plugins {
  kotlin("jvm") version "1.2.41"
  `maven-publish`
  id("com.jfrog.bintray") version "1.8.0"
}

group = "info.jdavid.server"
version = "1.0.0.11"

repositories {
  jcenter()
}

dependencies {
  compile(kotlin("stdlib-jdk8"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.22.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-nio:0.22.5")
  implementation("org.slf4j:slf4j-api:1.7.25")
  implementation("com.codahale:aes-gcm-siv:0.4.2")
  testImplementation("junit:junit:4.12")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.9.5")
  testImplementation("org.apache.httpcomponents:httpclient:4.5.5")
  testRuntime("org.slf4j:slf4j-jdk14:1.7.25")
}

kotlin {
  experimental.coroutines = Coroutines.ENABLE
}

val jarAll by tasks.creating(Jar::class) {
  baseName = "${project.name}-all"
  manifest {
    attributes["Main-Class"] = "info.jdavid.server.http.FileHandler"
  }
  from(configurations.runtime.map { if (it.isDirectory) it as Any else zipTree(it) })
}

val sourcesJar by tasks.creating(Jar::class) {
  classifier = "sources"
  from(java.sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
  classifier = "javadoc"
  from(java.docsDir)
}

tasks.withType(KotlinJvmCompile::class.java).all {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

val javadoc: Javadoc by tasks
val jar: Jar by tasks
jar.apply {
  manifest {
    attributes["Sealed"] = true
  }
}

tasks {
  "javadocJar" {
    dependsOn(javadoc)
  }
  "jar" {
    dependsOn(jarAll)
  }
}

publishing {
  repositories {
    maven {
      url = uri("${buildDir}/repo")
    }
  }
  (publications) {
    "mavenJava"(MavenPublication::class) {
      from(components["java"])
      artifact(sourcesJar)
      artifact(javadocJar)
    }
  }
}

bintray {
  user = "programingjd"
  key = {
    "bintrayApiKey".let { key: String ->
      File("local.properties").readLines().findLast {
        it.startsWith("${key}=")
      }?.substring(key.length + 1)
    }
  }()
  //dryRun = true
  publish = true
  setPublications("mavenJava")
  pkg(delegateClosureOf<BintrayExtension.PackageConfig>{
    repo = "maven"
    name = "${project.group}"
    websiteUrl = "https://github.com/programingjd/server"
    issueTrackerUrl = "https://github.com/programingjd/server/issues"
    vcsUrl = "https://github.com/programingjd/server.git"
    githubRepo = "programingjd/server"
    githubReleaseNotesFile = "README.md"
    setLicenses("Apache-2.0")
    setLabels("server", "http", "java", "kotlin", "coroutines")
    publicDownloadNumbers = true
    version(delegateClosureOf<BintrayExtension.VersionConfig> {
      name = "${project.version}"
      mavenCentralSync(delegateClosureOf<BintrayExtension.MavenCentralSyncConfig> {
        sync = false
      })
    })
  })
}

