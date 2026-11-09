import Dependencies.Versions.*

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  // the default HTTP engine (priority 0 - any other engine on the classpath wins)
  "io.cequence" %% "ws-client-jdk" % wsClient,
  // we ship our own version of json-repair (originally in Python)
  "io.cequence" %% "json-repair" % wsClient,
  // logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
)
