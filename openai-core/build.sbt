import sbt.Keys.test

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

def akkaStreamLibs(scalaVersion: String): Seq[ModuleID] = {
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) =>
      Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.1"
      )
    case Some((2, 13)) =>
      Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.20"
      )
    case Some((3, _)) =>
      // because of the conflicting cross-version suffixes 2.13 vs 3
      Seq(
        "com.typesafe.akka" % "akka-stream_2.13" % "2.6.20" exclude ("com.typesafe", "ssl-config-core_2.13"),
        "com.typesafe" %% "ssl-config-core" % "0.6.1"
      )
    case _ =>
      throw new Exception("Unsupported scala version")
  }
}

libraryDependencies ++= akkaStreamLibs(scalaVersion.value)
