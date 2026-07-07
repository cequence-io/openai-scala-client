import Dependencies.Versions._

name := "openai-scala-google-vertexai-client"

description := "OpenAI API wrapper for Google VertexAI."

libraryDependencies ++= Seq(
  "com.google.cloud" % "google-cloud-vertexai" % "1.52.0",
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient
)
