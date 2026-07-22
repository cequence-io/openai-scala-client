import Dependencies.Versions._

name := "openai-scala-claude-agent-client"

description := "Scala client for the Claude Code CLI subprocess transport (Claude Agent SDK-compatible)."

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.2.18",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.scalamock" %% "scalamock" % scalaMock % Test
)
