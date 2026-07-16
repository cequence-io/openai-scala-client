import Dependencies.Versions._

name := "openai-scala-anthropic-client"

description := "Scala client for Anthropic API implemented using Play WS lib."

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient,
  "io.cequence" %% "ws-client-play-stream" % wsClient,
  "org.scalactic" %% "scalactic" % "3.2.20",
  "org.scalatest" %% "scalatest" % "3.2.20" % Test,
  "org.scalamock" %% "scalamock" % scalaMock % Test
)
