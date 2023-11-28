import sbt.Keys.test

name := "openai-scala-core"

description := "Core module of OpenAI Scala client"

lazy val akkaVersion = settingKey[String]("Akka version to use")

akkaVersion := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => "2.6.1"
    case Some((2, 13)) => "2.6.20"
    case Some((3, _)) => "2.6.20"
    case _ => "2.6.20"
  }
}

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion.value
