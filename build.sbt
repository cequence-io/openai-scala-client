import sbt.Keys.test

// Supported versions
val scala212 = "2.12.18"
val scala213 = "2.13.11"
val scala3 = "3.2.2"

ThisBuild / organization := "io.cequence"
ThisBuild / scalaVersion := scala212
ThisBuild / version := "1.0.1-RC.1"
ThisBuild / isSnapshot := false

lazy val commonSettings = Seq(
  libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.16",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test,
  libraryDependencies += "org.scalatestplus" %% "mockito-4-11" % "3.2.16.0" % Test,
  libraryDependencies ++= extraTestDependencies(scalaVersion.value),
  crossScalaVersions := List(scala212, scala213, scala3)
)

def extraTestDependencies(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) =>
      Seq(
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.1" % Test
      )

    case Some((2, 13)) =>
      Seq(
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.20" % Test
      )

    case Some((3, _)) =>
      Seq(
        // because of conflicting cross-version suffixes 2.13 vs 3 - scala-java8-compat, etc
        "com.typesafe.akka" % "akka-actor-testkit-typed_2.13" % "2.6.20" % Test
      )

    case _ =>
      Nil
  }

lazy val core = (project in file("openai-core")).settings(commonSettings *)

lazy val client =
  (project in file("openai-client")).settings(commonSettings *).dependsOn(core).aggregate(core)

lazy val client_stream = (project in file("openai-client-stream"))
  .settings(commonSettings *)
  .dependsOn(client)
  .aggregate(client)

// note that for anthropic_client we provide a streaming extension within the module as well
lazy val anthropic_client = (project in file("anthropic-client"))
  .settings(commonSettings *)
  .dependsOn(core, client, client_stream)
  .aggregate(core, client, client_stream)

lazy val google_vertexai_client = (project in file("google-vertexai-client"))
  .settings(commonSettings *)
  .dependsOn(core, client, client_stream)
  .aggregate(core, client, client_stream)

lazy val count_tokens = (project in file("openai-count-tokens"))
  .settings(
    (commonSettings ++ Seq(definedTestNames in Test := Nil)) *
  )
  .dependsOn(client)
  .aggregate(anthropic_client, google_vertexai_client)

lazy val guice = (project in file("openai-guice"))
  .settings(commonSettings *)
  .dependsOn(client)
  .aggregate(count_tokens)

lazy val examples = (project in file("openai-examples"))
  .settings(commonSettings *)
  .dependsOn(client_stream, anthropic_client)
  .aggregate(client_stream, anthropic_client)

// POM settings for Sonatype
ThisBuild / homepage := Some(
  url("https://github.com/cequence-io/openai-scala-client")
)

ThisBuild / sonatypeProfileName := "io.cequence"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/cequence-io/openai-scala-client"),
    "scm:git@github.com:cequence-io/openai-scala-client.git"
  )
)

ThisBuild / developers := List(
  Developer(
    "bnd",
    "Peter Banda",
    "peter.banda@protonmail.com",
    url("https://peterbanda.net")
  )
)

ThisBuild / licenses += "MIT" -> url("https://opensource.org/licenses/MIT")

ThisBuild / publishMavenStyle := true

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias(
  "validateCode",
  List(
    "scalafix",
    "scalafmtSbtCheck",
    "scalafmtCheckAll",
    "test:scalafix",
    "test:scalafmtCheckAll"
  ).mkString(";")
)

addCommandAlias(
  "formatCode",
  List(
    "scalafmt",
    "scalafmtSbt",
    "Test/scalafmt"
  ).mkString(";")
)

addCommandAlias(
  "testWithCoverage",
  List(
    "coverage",
    "test",
    "coverageReport"
  ).mkString(";")
)

inThisBuild(
  List(
    scalacOptions += "-Ywarn-unused",
//    scalaVersion := "2.12.15",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)
