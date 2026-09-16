import Dependencies.Versions._

name := "openai-scala-typesafe-client"

description := "Scala client for TypeSafe AI - the System One decision API (Jev) - implemented using Play WS lib."

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play-akka" % wsClient
)
