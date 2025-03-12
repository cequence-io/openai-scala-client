import Dependencies.Versions.*

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  // we ship our own version of json-repair (originally in Python)
  "io.cequence" %% "json-repair" % wsClient,
  // logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.14" // requires JDK11, in order to use JDK8 switch to 1.3.5
)
