name := "openai-scala-client"

description := "Scala client for OpenAI API implemented using Play WS lib."

val playWsVersion = "2.1.10"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion
)

// POM settings for Sonatype
homepage := Some(url("https://github.com/cequence-io/openai-scala-client"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/cequence-io/openai-scala-3-client"), "scm:git@github.com:cequence-io/openai-scala-3-client.git"))

developers := List(
  Developer("bnd", "Peter Banda", "peter.banda@protonmail.com", url("https://peterbanda.net"))
)

licenses += "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")

publishMavenStyle := true

// publishTo := sonatypePublishTo.value

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
