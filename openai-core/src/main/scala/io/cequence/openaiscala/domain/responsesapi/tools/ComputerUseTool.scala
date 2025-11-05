package io.cequence.openaiscala.domain.responsesapi.tools

/**
 * A tool that controls a virtual computer.
 *
 * @param displayHeight
 *   The height of the computer display (in pixels).
 * @param displayWidth
 *   The width of the computer display (in pixels).
 * @param environment
 *   The type of computer environment to control.
 */
case class ComputerUseTool(
  displayHeight: Int,
  displayWidth: Int,
  environment: String
) extends Tool {
  override val `type`: String = "computer_use_preview"
}
