import sbt.Keys.test

// Supported versions
val scala212 = "2.12.18"
val scala213 = "2.13.18"
val scala3 = "3.2.2"
// the Scala 3 line of the ZIO module: ws-client-zio's `_3` is built with 3.3 (TASTy 28.3)
val scala33 = "3.3.8"

ThisBuild / organization := "io.cequence"
ThisBuild / scalaVersion := scala213
ThisBuild / version := "2.0.0-SNAPSHOT"
ThisBuild / isSnapshot := true

lazy val commonSettings = Seq(
  libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.16",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test,
  libraryDependencies += "org.scalatestplus" %% "mockito-4-11" % "3.2.16.0" % Test,
  // logging is the caller's choice since 2.0.0 - logback only for our own tests
  libraryDependencies += "ch.qos.logback" % "logback-classic" % Dependencies.Versions.logback % Test,
  scalacOptions += "-feature",
  crossScalaVersions := List(scala212, scala213, scala3)
)

// A 3.2-line module (its `_3` artifact compiled with 3.2.2) that also builds with 3.3: an sbt
// project dependency needs one Scala line on both sides, so the 3.3-line ZIO module depends on
// a 3.3 build of it. That build is never published (`_3` must come from 3.2.2 only)
lazy val alsoOnScala33 = {
  val onScala33 = Def.setting(scalaVersion.value == scala33)
  Seq(
    crossScalaVersions := List(scala212, scala213, scala3, scala33),
    publish / skip := onScala33.value,
    publishLocal / skip := onScala33.value,
    publishM2 / skip := onScala33.value
  )
}

// unpublished test support shared by every module's tests (stream runners, test engines)
lazy val testkit = (project in file("openai-testkit")).settings(
  (commonSettings ++ alsoOnScala33 ++ Seq(
    name := "openai-scala-testkit",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "io.cequence" %% "ws-client-core" % Dependencies.Versions.wsClient,
      "org.scalatest" %% "scalatest" % "3.2.16"
    )
  )) *
)

lazy val core =
  (project in file("openai-core"))
    .settings((commonSettings ++ alsoOnScala33) *)
    .dependsOn(testkit % Test)

lazy val client =
  (project in file("openai-client"))
    .settings((commonSettings ++ alsoOnScala33) *)
    .dependsOn(core, testkit % Test)
    .aggregate(core)

lazy val client_stream = (project in file("openai-client-stream"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(client, testkit % Test)
  .aggregate(client)

// ZIO-native views of the services (Task / ZStream), generated from the service traits by
// project/ZioServiceGenerator.scala
lazy val zio = (project in file("openai-zio"))
  .settings(
    (commonSettings ++ Seq(
      name := "openai-scala-zio",
      crossScalaVersions := List(scala212, scala213, scala33),
      libraryDependencies += "io.cequence" %% "ws-client-zio" % Dependencies.Versions.wsClient,
      // the native ZIO engine for the round-trip specs (src/test/scala-2): zio-http publishes no
      // Scala 3 artifacts for ws-client's line
      libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq("io.cequence" %% "ws-client-zio-http" % Dependencies.Versions.wsClient % Test)
        case _ =>
          Nil
      }),
      Compile / sourceGenerators += Def.task {
        val coreServices =
          (LocalProject(
            "core"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/service"
        val traits = Seq(
          "OpenAIChatCompletionService",
          "OpenAICompletionService",
          "OpenAICoreService",
          "OpenAIResponsesService",
          "OpenAIAgentsService",
          "OpenAIDecisionsService",
          "OpenAIGraderService",
          "OpenAIChatCompletionBatchService",
          "OpenAIService",
          "OpenAIChatCompletionStreamedServiceExtra",
          "OpenAIStreamedServiceExtra"
        ).map(name => ZioServiceGenerator.ServiceTrait(coreServices / s"$name.scala", name))
        val extensions = Seq(
          ZioServiceGenerator.Extension(
            coreServices / "OpenAIChatCompletionExtra.scala",
            "OpenAIChatCompletionExtra",
            "OpenAIChatCompletionImplicits"
          )
        )
        ZioServiceGenerator
          .generate(traits ++ extensions, (Compile / sourceManaged).value / "zio")
      }.taskValue
    )) *
  )
  .dependsOn(client_stream, testkit % Test)

// note that for anthropic_client we provide a streaming extension within the module as well
lazy val anthropic_client = (project in file("anthropic-client"))
  .settings(commonSettings *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

lazy val google_vertexai_client = (project in file("google-vertexai-client"))
  .settings(commonSettings *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

lazy val google_gemini_client = (project in file("google-gemini-client"))
  .settings(commonSettings *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

// note that for perplexity_client we provide a streaming extension within the module as well
lazy val perplexity_sonar_client = (project in file("perplexity-sonar-client"))
  .settings(commonSettings *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

// TypeSafe AI System One API (typed decisions, not chat) - plain request/response, no streaming,
// so it needs neither the OpenAI client nor the stream module
lazy val typesafe_client = (project in file("typesafe-client"))
  .settings(commonSettings *)
  .dependsOn(core, testkit % Test)
  .aggregate(core)

// subprocess transport (spawns the `claude` CLI) - no ws-client/HTTP engine involved; depends
// on anthropic_client to reuse its Content/ContentBlock domain model + JSON formats for the
// embedded Anthropic Messages API objects the CLI emits
lazy val claude_agent_client = (project in file("claude-agent-client"))
  .settings(commonSettings *)
  .dependsOn(core, anthropic_client, testkit % Test)
  .aggregate(core, anthropic_client)

lazy val count_tokens = (project in file("openai-count-tokens"))
  .settings(
    (commonSettings ++ Seq(definedTestNames in Test := Nil)) *
  )
  .dependsOn(client)
  .aggregate(
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client,
    typesafe_client,
    claude_agent_client
  )

lazy val all = (project in file("openai-all"))
  .settings(commonSettings *)
  .dependsOn(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    google_gemini_client,
    perplexity_sonar_client,
    typesafe_client,
    claude_agent_client,
    count_tokens
  )

lazy val guice = (project in file("openai-guice"))
  .settings(commonSettings *)
  .dependsOn(client)
  .aggregate(count_tokens, all)

lazy val examples = (project in file("openai-examples"))
  .settings(
    (commonSettings ++ Seq(
      run / fork := true,
      run / connectInput := true,
      run / outputStrategy := Some(StdoutOutput)
    )) *
  )
  .dependsOn(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client,
    typesafe_client,
    claude_agent_client
  )
  .aggregate(
    client_stream,
    anthropic_client,
    google_vertexai_client,
    perplexity_sonar_client,
    google_gemini_client,
    typesafe_client,
    claude_agent_client
  )

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
//    scalaVersion := scala3,
    semanticdbEnabled := true,
    // scalafix 0.11.0 bundles semanticdb 4.7.8, which has no build for Scala 2.13.18
    semanticdbVersion := "4.13.10",
    scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
  )
)
