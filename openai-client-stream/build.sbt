name := "openai-scala-client-stream"

description := "Stream support for the OpenAI Scala client."

val akkaHttpVersion = "10.5.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion  // JSON WS Streaming
)