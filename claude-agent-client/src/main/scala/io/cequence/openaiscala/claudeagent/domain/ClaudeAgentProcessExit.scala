package io.cequence.openaiscala.claudeagent.domain

/** Final status of the `claude` subprocess backing a session. */
case class ClaudeAgentProcessExit(
  exitCode: Option[Int],
  signal: Option[String],
  stderrTail: String
)
