// A consumer of the published Scala 3 artifacts, outside the library's build: the
// migration guide's snippets (docs/migration-2.0.md, the "after" code) compiled with -feature
// -Werror - Akka on Scala 3.2.2, Pekko / fs2 / ZIO on 3.3 - and a stream per consumer run
// against a local server, its bridges counted. Run it with ./run.sh: the library is published to
// a private Ivy home first (never ~/.ivy2/local).

// the library's version and its ws-client version, read from the library's own build - a release
// commit (version X) is checked against X; one version for every ws-client artifact
def fromLibraryBuild(
  path: String,
  pattern: String
): String = {
  val source = IO.read(file(s"../../$path"))
  pattern.r
    .findFirstMatchIn(source)
    .map(_.group(1))
    .getOrElse(sys.error(s"No match for $pattern in ../../$path"))
}

val openaiVersion = fromLibraryBuild("build.sbt", "ThisBuild / version := \"([^\"]+)\"")
val wsClient = fromLibraryBuild("project/Dependencies.scala", "val wsClient = \"([^\"]+)\"")

def consumer(scala: String, deps: ModuleID*) = Seq(
  scalaVersion := scala,
  scalacOptions ++= Seq("-feature", "-Werror"),
  Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "shared",
  // logback at WARN (shared/resources/logback.xml): at DEBUG the HTTP clients log whole requests
  Compile / unmanagedResourceDirectories += (ThisBuild / baseDirectory).value / "shared" / "resources",
  libraryDependencies ++= deps,
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.14",
  run / fork := true
)

lazy val akka = (project in file("akka")).settings(
  consumer(
    "3.2.2",
    "io.cequence" %% "openai-scala-client-stream" % openaiVersion,
    "io.cequence" %% "ws-client-play-akka-stream" % wsClient
  ) *
)

lazy val pekko = (project in file("pekko")).settings(
  consumer(
    "3.3.8",
    "io.cequence" %% "openai-scala-client-stream" % openaiVersion,
    "io.cequence" %% "ws-client-play-pekko-stream" % wsClient
  ) *
)

lazy val fs2 = (project in file("fs2")).settings(
  consumer(
    "3.3.8",
    "io.cequence" %% "openai-scala-client-stream" % openaiVersion,
    "io.cequence" %% "ws-client-fs2" % wsClient
  ) *
)

lazy val zio = (project in file("zio")).settings(
  consumer("3.3.8", "io.cequence" %% "openai-scala-zio" % openaiVersion) *
)
