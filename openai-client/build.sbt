import Dependencies.Versions._

name := "openai-scala-client"

description := "Scala client for OpenAI API implemented using Play WS lib."

lazy val playWsVersion = settingKey[String]("Play WS version to use")

playWsVersion := {
  scalaVersion.value match {
    case "2.12.18" => "2.1.10"
    case "2.13.11" => "2.2.0-M3"
    case "3.2.2" =>
      "2.2.0-M2" // Version "2.2.0-M3" was produced by an unstable release: Scala 3.3.0-RC3
    case _ => "2.1.10"
  }
}

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion.value,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion.value,
  "io.cequence" %% "ws-client-core" % wsClient,
  "io.cequence" %% "ws-client-play" % wsClient,
  "io.cequence" %% "ws-client-stream" % wsClient
)

libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.18"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
libraryDependencies += "org.scalamock" %% "scalamock" % scakaMock % Test

//libraryDependencies ++= Seq(
//  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
//  "ch.qos.logback" % "logback-classic" % "1.4.7"
//)
