import sbt.Keys.test

name := "openai-scala-count-tokens"

description := "Module of OpenAI Scala client to count tokens before sending a request to ChatGPT"

libraryDependencies ++= {
  val jTokkitV = "0.5.0"
  Seq(
    "com.knuddels" % "jtokkit" % jTokkitV
  )
}
