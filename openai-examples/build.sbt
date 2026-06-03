name := "openai-examples"

description := "Examples demonstrating the use of the OpenAI Scala client"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.14" // requires JDK11, in order to use JDK8 switch to 1.3.5
)
