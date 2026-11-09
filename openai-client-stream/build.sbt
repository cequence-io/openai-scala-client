import Dependencies.Versions._

name := "openai-scala-client-stream"

description := "Stream support for the OpenAI Scala client."

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient
)
