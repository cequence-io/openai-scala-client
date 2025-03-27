package io.cequence.openaiscala.domain.responsesapi.tools

/**
 * A tool that controls a virtual computer.
 *
 * @param displayHeight
 *   The height of the computer display.
 * @param displayWidth
 *   The width of the computer display.
 * @param environment
 *   The type of computer environment to control.
 */
case class ComputerUseTool(
  displayHeight: Double,
  displayWidth: Double,
  environment: String
) extends Tool {
  val `type`: String = "computer_use_preview"

  override def typeString: String = `type`
}
