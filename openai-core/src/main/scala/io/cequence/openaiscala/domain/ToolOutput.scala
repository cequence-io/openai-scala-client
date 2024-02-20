package io.cequence.openaiscala.domain

/**
 * A tool for which output is being submitted to continue the run.
 *
 * @param toolCallId
 *   The ID of the tool call in the required_action object within the run object the output is
 *   being submitted for.
 * @param output
 *   The output of the tool call to be submitted to continue the run.
 */
final case class ToolOutput(
  toolCallId: Option[String],
  output: Option[String]
)
