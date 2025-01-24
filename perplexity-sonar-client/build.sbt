import Dependencies.Versions._

name := "openai-scala-perplexity-sonar-client"

description := "Scala client for Perplexity - Sonar API implemented using Play WS lib."

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient,
  "io.cequence" %% "ws-client-play-stream" % wsClient,
  "org.scalactic" %% "scalactic" % "3.2.18",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.scalamock" %% "scalamock" % scalaMock % Test
)
