import Dependencies.Versions._

name := "openai-scala-client"

description := "Scala client for OpenAI API implemented using Play WS lib."

libraryDependencies ++= Seq(
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient
)

libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.18"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % scakaMock % Test

//libraryDependencies ++= Seq(
//  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
//  "ch.qos.logback" % "logback-classic" % "1.4.7"
//)
