import sbt.Keys.test

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

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

def akkaStreamLibs(scalaVersion: String): Seq[ModuleID] = {
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) =>
      Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.1",
      )
    case Some((2, 13)) =>
      Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.20",
      )
    case Some((3, _)) =>
      // because of the conflicting cross-version suffixes 2.13 vs 3
      Seq(
        "com.typesafe.akka" % "akka-stream_2.13" % "2.6.20" exclude ("com.typesafe", "ssl-config-core_2.13"),
        "com.typesafe" %% "ssl-config-core" % "0.6.1",
      )
    case _ =>
      throw new Exception("Unsupported scala version")
  }
}

libraryDependencies ++= akkaStreamLibs(scalaVersion.value)
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion.value,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion.value,
  "io.cequence" %% "ws-client-core" % "0.2.0",
  "io.cequence" %% "ws-client-play" % "0.2.0"
)
