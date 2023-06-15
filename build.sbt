import sbt.Keys.test

// Supported versions
val scala212 = "2.12.18"
val scala213 = "2.13.11"
val scala3 = "3.2.2"

ThisBuild / organization := "io.cequence"
ThisBuild / scalaVersion := scala212
ThisBuild / version := "0.4.0"
ThisBuild / isSnapshot := false

lazy val core = (project in file("openai-core"))

lazy val client = (project in file("openai-client"))
  .dependsOn(core)
  .aggregate(core)

lazy val client_stream = (project in file("openai-client-stream"))
  .dependsOn(client)
  .aggregate(client)

lazy val guice = (project in file("openai-guice"))
  .dependsOn(client)
  .aggregate(client_stream)


// POM settings for Sonatype
ThisBuild / homepage := Some(url("https://github.com/cequence-io/openai-scala-client"))

ThisBuild / sonatypeProfileName := "io.cequence"

ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/cequence-io/openai-scala-client"), "scm:git@github.com:cequence-io/openai-scala-client.git"))

ThisBuild / developers := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net"))
)

ThisBuild / licenses += "MIT" -> url("https://opensource.org/licenses/MIT")

ThisBuild / publishMavenStyle := true

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

ThisBuild / publishTo := sonatypePublishToBundle.value