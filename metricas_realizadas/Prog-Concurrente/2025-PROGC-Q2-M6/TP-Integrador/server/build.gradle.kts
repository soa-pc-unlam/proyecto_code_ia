plugins {
  kotlin("jvm") version "2.2.0"
  application
  id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "com.example"
version = "1.0.0"

repositories {
  mavenCentral()
  maven {
    url = uri("https://jitpack.io")
  }
}

dependencies {
  implementation(kotlin("stdlib"))

  // --- Jetty Core
  implementation("org.eclipse.jetty:jetty-server:11.0.20")
  implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
  implementation("org.eclipse.jetty.websocket:websocket-jetty-server:11.0.20")
  implementation("org.eclipse.jetty.websocket:websocket-servlet:11.0.20")


  implementation("com.github.bhlangonijr:chesslib:1.3.3")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

  implementation("ch.qos.logback:logback-classic:1.5.6")

  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("ChessServer")
            mainClass.set("MainKt")
            buildArgs.add("--no-fallback")
        }
    }
    toolchainDetection.set(false)
}