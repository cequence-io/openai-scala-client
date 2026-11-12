import sbt.Keys.test

import scala.xml.{Elem, Node => XmlNode}
import scala.xml.transform.{RewriteRule, RuleTransformer}

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
  crossScalaVersions := List(scala212, scala213, scala3),
  // the unpublished testkit (every module's test dependency) stays out of the published POMs
  pomPostProcess := { (node: XmlNode) =>
    val dropTestkit = new RewriteRule {
      override def transform(n: XmlNode): Seq[XmlNode] =
        n match {
          case e: Elem
              if e.label == "dependency" &&
                (e \ "artifactId").text.startsWith("openai-scala-testkit") =>
            Nil
          case other =>
            other
        }
    }
    new RuleTransformer(dropTestkit).transform(node).head
  }
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
        def traits(
          dir: File,
          names: String*
        ) =
          names.map(name => ZioServiceGenerator.ServiceTrait(dir / s"$name.scala", name))

        val coreServices =
          (LocalProject(
            "core"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/service"
        val anthropicServices =
          (LocalProject(
            "anthropic_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/anthropic/service"
        val geminiServices =
          (LocalProject(
            "google_gemini_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/gemini/service"
        val vertexAIServices =
          (LocalProject(
            "google_vertexai_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/vertexai/service"
        val sonarServices =
          (LocalProject(
            "perplexity_sonar_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/perplexity/service"
        val typeSafeServices =
          (LocalProject(
            "typesafe_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/typesafe/service"
        val claudeAgentServices =
          (LocalProject(
            "claude_agent_client"
          ) / Compile / scalaSource).value / "io/cequence/openaiscala/claudeagent/service"
        val openAI = traits(
          coreServices,
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
        )
        // the providers' native services (their OpenAI adapters are covered by the views above)
        val providers =
          traits(
            anthropicServices,
            "AnthropicMessageBatchService",
            "AnthropicManagedAgentService",
            "AnthropicEnvironmentService",
            "AnthropicSessionService",
            "AnthropicDeploymentService",
            "AnthropicVaultService",
            "AnthropicMemoryStoreService",
            "AnthropicService",
            "AnthropicBedrockBatchInferenceService"
          ) ++
            traits(geminiServices, "GeminiService") ++
            traits(
              vertexAIServices,
              "VertexAIBatchPredictionService"
            ) ++
            traits(sonarServices, "SonarService") ++
            traits(typeSafeServices, "TypeSafeService") ++
            traits(
              claudeAgentServices,
              "ClaudeAgentService"
            )
        val extensions = Seq(
          ZioServiceGenerator.Extension(
            coreServices / "OpenAIChatCompletionExtra.scala",
            "OpenAIChatCompletionExtra",
            "OpenAIChatCompletionImplicits"
          )
        )
        ZioServiceGenerator
          .generate(openAI ++ providers ++ extensions, (Compile / sourceManaged).value / "zio")
      }.taskValue
    )) *
  )
  // the provider modules are `provided`: a provider's view needs its module, which whoever uses
  // the view has anyway - the ZIO module pulls in none of them
  .dependsOn(
    client_stream,
    anthropic_client % Provided,
    google_vertexai_client % Provided,
    google_gemini_client % Provided,
    perplexity_sonar_client % Provided,
    typesafe_client % Provided,
    claude_agent_client % Provided,
    testkit % Test
  )

// note that for anthropic_client we provide a streaming extension within the module as well
lazy val anthropic_client = (project in file("anthropic-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

lazy val google_vertexai_client = (project in file("google-vertexai-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

lazy val google_gemini_client = (project in file("google-gemini-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

// note that for perplexity_client we provide a streaming extension within the module as well
lazy val perplexity_sonar_client = (project in file("perplexity-sonar-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(core, testkit % Test)
  .aggregate(core, client, client_stream)

// TypeSafe AI System One API (typed decisions, not chat) - plain request/response, no streaming,
// so it needs neither the OpenAI client nor the stream module
lazy val typesafe_client = (project in file("typesafe-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
  .dependsOn(core, testkit % Test)
  .aggregate(core)

// subprocess transport (spawns the `claude` CLI) - no ws-client/HTTP engine involved; depends
// on anthropic_client to reuse its Content/ContentBlock domain model + JSON formats for the
// embedded Anthropic Messages API objects the CLI emits
lazy val claude_agent_client = (project in file("claude-agent-client"))
  .settings((commonSettings ++ alsoOnScala33) *)
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

// Small unpublished projects showing the stream interop - one each, since the Akka and Pekko
// flavours of ws-client cannot share a classpath
lazy val interopExampleSettings = Seq(
  scalacOptions += "-feature",
  publish / skip := true,
  run / fork := true,
  run / connectInput := true,
  run / outputStrategy := Some(StdoutOutput),
  libraryDependencies += "ch.qos.logback" % "logback-classic" % Dependencies.Versions.logback
)

lazy val examples_akka = (project in file("openai-examples-akka"))
  .settings(
    (interopExampleSettings ++ Seq(
      name := "openai-examples-akka",
      crossScalaVersions := List(scala212, scala213, scala3),
      libraryDependencies +=
        "io.cequence" %% "ws-client-play-akka-stream" % Dependencies.Versions.wsClient
    )) *
  )
  .dependsOn(client_stream)

// Pekko's Play WS engine is built for Scala 2.13 and 3.3 only
lazy val examples_pekko = (project in file("openai-examples-pekko"))
  .settings(
    (interopExampleSettings ++ Seq(
      name := "openai-examples-pekko",
      crossScalaVersions := List(scala213),
      libraryDependencies +=
        "io.cequence" %% "ws-client-play-pekko-stream" % Dependencies.Versions.wsClient
    )) *
  )
  .dependsOn(client_stream)

// fs2 / cats-effect publish Scala 3 artifacts for 3.3+ only
lazy val examples_fs2 = (project in file("openai-examples-fs2"))
  .settings(
    (interopExampleSettings ++ Seq(
      name := "openai-examples-fs2",
      crossScalaVersions := List(scala212, scala213),
      libraryDependencies += "io.cequence" %% "ws-client-fs2" % Dependencies.Versions.wsClient
    )) *
  )
  .dependsOn(client_stream)

// zio-http publishes no Scala 3 artifacts for ws-client's line
lazy val examples_zio = (project in file("openai-examples-zio"))
  .settings(
    (interopExampleSettings ++ Seq(
      name := "openai-examples-zio",
      crossScalaVersions := List(scala212, scala213),
      libraryDependencies +=
        "io.cequence" %% "ws-client-zio-http" % Dependencies.Versions.wsClient
    )) *
  )
  .dependsOn(zio)

// The engine x consumer matrix (unpublished, tests only): each project runs the specs of the
// stream modules (`test->test`) in a forked JVM on one engine (`-Dws-client.engine`) and one
// consumer (`-Dopenai.test.consumer`, a testkit StreamRunner registered by the project). Where
// engine and consumer are the same library, every run through the testkit must build no bridge
// (`-Dopenai.test.expectedBridges=0`): the wire specs double as native round-trip checks.
def matrixSettings(
  engine: String,
  consumer: String,
  expectedBridges: Option[Int]
): Seq[Setting[_]] =
  Seq(
    scalacOptions += "-feature",
    publish / skip := true,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.16" % Test,
    Test / fork := true,
    Test / javaOptions ++= Seq(
      s"-Dws-client.engine=$engine",
      s"-Dopenai.test.consumer=$consumer"
    ) ++ expectedBridges.map(n => s"-Dopenai.test.expectedBridges=$n")
  )

// the specs of the stream modules, run by every matrix project but the no-stream one
lazy val matrixSpecs = Test / definedTests ++= Seq(
  (core / Test / definedTests).value,
  (client_stream / Test / definedTests).value,
  (anthropic_client / Test / definedTests).value,
  (google_gemini_client / Test / definedTests).value,
  (google_vertexai_client / Test / definedTests).value,
  (perplexity_sonar_client / Test / definedTests).value,
  (claude_agent_client / Test / definedTests).value
).flatten

lazy val matrixModules: Seq[ClasspathDep[ProjectReference]] = Seq(
  core % "test->test",
  client_stream % "test->test",
  anthropic_client % "test->test",
  google_gemini_client % "test->test",
  google_vertexai_client % "test->test",
  perplexity_sonar_client % "test->test",
  claude_agent_client % "test->test"
)

lazy val matrix_akka = (project in file("openai-matrix/akka"))
  .settings(
    (matrixSettings("play-akka-stream", "akka", Some(0)) ++ Seq(
      name := "openai-matrix-akka",
      crossScalaVersions := List(scala212, scala213, scala3),
      libraryDependencies +=
        "io.cequence" %% "ws-client-play-akka-stream" % Dependencies.Versions.wsClient % Test,
      matrixSpecs
    )) *
  )
  .dependsOn(matrixModules *)

// Pekko's engines are built for Scala 2.13 and 3.3 only; no Akka flavour on this classpath
lazy val matrix_pekko = (project in file("openai-matrix/pekko"))
  .settings(
    (matrixSettings("play-pekko-stream", "pekko", Some(0)) ++ Seq(
      name := "openai-matrix-pekko",
      crossScalaVersions := List(scala213),
      libraryDependencies ++= Seq(
        "io.cequence" %% "ws-client-play-pekko-stream" % Dependencies.Versions.wsClient % Test,
        "io.cequence" %% "ws-client-pekko-http" % Dependencies.Versions.wsClient % Test
      ),
      matrixSpecs
    )) *
  )
  .dependsOn(matrixModules *)

// zio-http publishes no Scala 3 artifacts for ws-client's line
lazy val matrix_zio = (project in file("openai-matrix/zio"))
  .settings(
    (matrixSettings("zio-http", "zio", Some(0)) ++ Seq(
      name := "openai-matrix-zio",
      crossScalaVersions := List(scala212, scala213),
      libraryDependencies +=
        "io.cequence" %% "ws-client-zio-http" % Dependencies.Versions.wsClient % Test,
      matrixSpecs,
      Test / definedTests ++= (zio / Test / definedTests).value
    )) *
  )
  // the zio module's specs need its `provided` provider modules (typesafe-client is no stream
  // module, so not in matrixModules)
  .dependsOn((matrixModules ++ Seq(zio % "test->test", typesafe_client % Test)) *)

// fs2 / cats-effect publish Scala 3 artifacts for 3.3+ only; on the jdk engine a run crosses one
// bridge per HTTP response, so no expectation is set here
lazy val matrix_fs2 = (project in file("openai-matrix/fs2"))
  .settings(
    (matrixSettings("jdk", "fs2", None) ++ Seq(
      name := "openai-matrix-fs2",
      crossScalaVersions := List(scala212, scala213),
      libraryDependencies +=
        "io.cequence" %% "ws-client-fs2" % Dependencies.Versions.wsClient % Test,
      matrixSpecs
    )) *
  )
  .dependsOn(matrixModules *)

// engines without output streaming (sttp, play-akka): its own spec only - the clear capability
// error, sync calls, binary-safe buffered downloads
lazy val matrix_nostream = (project in file("openai-matrix/nostream"))
  .settings(
    (matrixSettings("sttp", "neutral", None) ++ Seq(
      name := "openai-matrix-nostream",
      crossScalaVersions := List(scala212, scala213),
      libraryDependencies ++= Seq(
        "io.cequence" %% "ws-client-sttp" % Dependencies.Versions.wsClient % Test,
        "io.cequence" %% "ws-client-play-akka" % Dependencies.Versions.wsClient % Test
      )
    )) *
  )
  .dependsOn(client_stream % "test->test", testkit % Test)

// CI's Scala lines (aggregates only - no sources, never published). The root aggregates every
// project, but `++<v>` leaves a project built for fewer Scala versions on 2.13 next to
// dependencies switched to <v> - an update conflict (`_3` and `_2.13` suffixes). So CI runs
// `++<v> "project <line>" ...` on the projects that build for <v>: the modules, examples and
// interop examples (ci_scala*) and, in a job of its own, the engine matrix (ci_matrix*).
def ciLine(
  id: String,
  versions: List[String],
  projects: Seq[ProjectReference]
): Project =
  Project(id, file(s"ci/$id"))
    .settings(
      crossScalaVersions := versions,
      publish / skip := true,
      publishLocal / skip := true,
      publishM2 / skip := true
    )
    .aggregate(projects *)

lazy val scala3LineProjects: Seq[ProjectReference] = Seq(
  testkit,
  core,
  client,
  client_stream,
  anthropic_client,
  google_vertexai_client,
  google_gemini_client,
  perplexity_sonar_client,
  typesafe_client,
  claude_agent_client,
  count_tokens,
  all,
  guice,
  examples,
  examples_akka
)

lazy val scala212LineProjects: Seq[ProjectReference] =
  scala3LineProjects ++ Seq[ProjectReference](zio, examples_fs2, examples_zio)

lazy val ci_scala212 = ciLine("ci_scala212", List(scala212), scala212LineProjects)

lazy val ci_scala213 =
  ciLine(
    "ci_scala213",
    List(scala213),
    scala212LineProjects :+ (examples_pekko: ProjectReference)
  )

lazy val ci_scala3 = ciLine("ci_scala3", List(scala3), scala3LineProjects)

// the ZIO module's Scala 3 line (its dependencies build on 3.3.8 unpublished, see alsoOnScala33)
lazy val ci_scala33 = ciLine("ci_scala33", List(scala33), Seq(zio))

lazy val ci_matrix212 = ciLine(
  "ci_matrix212",
  List(scala212),
  Seq(matrix_akka, matrix_zio, matrix_fs2, matrix_nostream)
)

lazy val ci_matrix213 = ciLine(
  "ci_matrix213",
  List(scala213),
  Seq(matrix_akka, matrix_pekko, matrix_zio, matrix_fs2, matrix_nostream)
)

lazy val ci_matrix3 = ciLine("ci_matrix3", List(scala3), Seq(matrix_akka))

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
