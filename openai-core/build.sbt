import Dependencies.Versions._

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient
)
