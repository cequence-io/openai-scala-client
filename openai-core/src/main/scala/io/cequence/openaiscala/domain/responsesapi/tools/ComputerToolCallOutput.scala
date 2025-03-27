package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.{Input, ModelStatus}

/**
 * The output of a computer tool call.
 *
 * @param callId
 *   The ID of the computer tool call that produced the output
 * @param output
 *   A computer screenshot image used with the computer use tool
 * @param `type`
 *   The type of the computer tool call output. Always computer_call_output
 * @param acknowledgedSafetyChecks
 *   The safety checks reported by the API that have been acknowledged by the developer
 * @param id
 *   The ID of the computer tool call output
 * @param status
 *   The status of the message input. One of in_progress, completed, or incomplete. Populated
 *   when input items are returned via API.
 */
final case class ComputerToolCallOutput(
  callId: String,
  output: ComputerScreenshot,
  acknowledgedSafetyChecks: Seq[AcknowledgedSafetyCheck] = Nil,
  id: Option[String] = None,
  status: Option[ModelStatus] = None // in_progress, completed, or incomplete
) extends Input {
  val `type`: String = "computer_call_output"
}

/**
 * Represents a safety check that has been acknowledged by the developer.
 *
 * @param code
 *   The type of the pending safety check.
 * @param id
 *   The ID of the pending safety check.
 * @param message
 *   Details about the pending safety check.
 */
final case class AcknowledgedSafetyCheck(
  code: String,
  id: String,
  message: String
)

/**
 * Represents a computer screenshot in a tool call output.
 *
 * @param fileId
 *   The identifier of an uploaded file that contains the screenshot.
 * @param imageUrl
 *   The URL of the screenshot image.
 */
final case class ComputerScreenshot(
  fileId: Option[String] = None,
  imageUrl: Option[String] = None
) {
  val `type`: String = "computer_screenshot"
}
