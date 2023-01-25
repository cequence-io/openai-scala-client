name := "openai-scala-guice"

description := "Guice/DI for OpenAI Scala Client"

libraryDependencies ++= Seq(
  "net.codingwell" %% "scala-guice" % "5.1.0"
)

// POM settings for Sonatype
homepage := Some(url("https://github.com/cequence/openai-scala-client"))

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/cequence/openai-scala-3-client"), "scm:git@github.com:cequence/openai-scala-3-client.git"))

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
