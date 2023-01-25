name := "openai-scala-guice"

description := "Guice/DI for OpenAI Scala Client"

libraryDependencies ++= Seq(
  "net.codingwell" %% "scala-guice" % "5.1.0"
)

// we need this for Scala 2.13
//dependencyOverrides ++= Seq(
//  "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
//)